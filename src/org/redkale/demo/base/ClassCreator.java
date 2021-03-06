/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.demo.base;

import java.io.*;
import static java.lang.Character.toUpperCase;
import java.sql.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public class ClassCreator {

    private static final String currentpkg = ClassCreator.class.getPackage().getName();

    private static final String jdbc_url = "jdbc:mysql://localhost:3306/center?autoReconnect=true&amp;characterEncoding=utf8";//数据库url

    private static final String jdbc_user = "root"; //数据库用户名

    private static final String jdbc_pwd = ""; //数据库密码

    public static void main(String[] args) throws Exception {

        String pkg = currentpkg.substring(0, currentpkg.lastIndexOf('.') + 1) + "xxx";  //与base同级的包名

        final String entityClass = "UserDetail";//类名

        final String superEntityClass = "";//父类名

        loadEntity(pkg, entityClass, superEntityClass); //Entity内容

    }

    private static void loadEntity(String pkg, String classname, String superclassname) throws Exception {
        String entityBody = createEntityContent(pkg, classname, superclassname); //源码内容
        final File entityFile = new File("src/" + pkg.replace('.', '/') + "/" + classname + ".java");
        if (entityFile.isFile()) throw new RuntimeException(classname + ".java 已经存在");
        FileOutputStream out = new FileOutputStream(entityFile);
        out.write(entityBody.getBytes("UTF-8"));
        out.close();
    }

    private static String createEntityContent(String pkg, String classname, String superclassname) throws Exception {
        com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource source = new com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource();
        source.setUrl(jdbc_url);  //数据库url
        source.setUser(jdbc_user); //数据库账号
        source.setPassword(jdbc_pwd); //数据库密码
        Connection conn = source.getConnection();
        String catalog = conn.getCatalog();
        String tableComment = "";
        Statement stmt = conn.createStatement();
        ResultSet tcs = stmt.executeQuery("SHOW CREATE TABLE " + classname.toLowerCase());
        Map<String, String> uniques = new HashMap<>();
        Map<String, String> indexs = new HashMap<>();
        if (tcs.next()) {
            final String createsql = tcs.getString(2);
            for (String str : createsql.split("\n")) {
                str = str.trim();
                if (str.startsWith("UNIQUE KEY ")) {
                    str = str.substring(str.indexOf('`') + 1);
                    uniques.put(str.substring(0, str.indexOf('`')), str.substring(str.indexOf('(') + 1, str.indexOf(')')));
                } else if (str.startsWith("KEY ")) {
                    str = str.substring(str.indexOf('`') + 1);
                    indexs.put(str.substring(0, str.indexOf('`')), str.substring(str.indexOf('(') + 1, str.indexOf(')')));
                }
            }
            int pos = createsql.indexOf("COMMENT='");
            if (pos > 0) {
                tableComment = createsql.substring(pos + "COMMENT='".length(), createsql.lastIndexOf('\''));
            } else {
                tableComment = "";
            }
        }
        stmt.close();
        DatabaseMetaData meta = conn.getMetaData();
        final Set<String> columns = new HashSet<>();
        if (superclassname != null && !superclassname.isEmpty()) {
            ResultSet rs = meta.getColumns(null, "%", superclassname.toLowerCase(), null);
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
            rs.close();
        }
        ResultSet rs = meta.getColumns(null, "%", classname.toLowerCase(), null);
//       ResultSetMetaData rsd = rs.getMetaData();
//       for(int i =1 ; i<=rsd.getColumnCount();i++) {
//           System.out.println(rsd.getColumnName(i));
//       }     
        StringBuilder sb = new StringBuilder();

        sb.append("package " + pkg + ";" + "\r\n\r\n");

        sb.append("import javax.persistence.*;\r\n");
        //sb.append("import org.redkale.util.*;\r\n");
        if (superclassname == null || superclassname.isEmpty()) {
            sb.append("import " + currentpkg + ".BaseEntity;\r\n");
        }
        sb.append("\r\n/**\r\n"
            + " *\r\n"
            + " * @author " + System.getProperty("user.name") + "\r\n"
            + " */\r\n");
        //if (classname.contains("Info")) sb.append("@Cacheable\r\n");        
        sb.append("@Table(catalog = \"" + catalog + "\"");
        if (!tableComment.isEmpty()) sb.append(", comment = \"" + tableComment + "\"");
        if (!uniques.isEmpty()) {
            sb.append("\r\n        , uniqueConstraints = {");
            boolean first = true;
            for (Map.Entry<String, String> en : uniques.entrySet()) {
                if (!first) sb.append(", ");
                sb.append("@UniqueConstraint(name = \"" + en.getKey() + "\", columnNames = {" + en.getValue().replace('`', '"') + "})");
                first = false;
            }
            sb.append("}");
        }
        if (!indexs.isEmpty()) {
            sb.append("\r\n        , indexes = {");
            boolean first = true;
            for (Map.Entry<String, String> en : indexs.entrySet()) {
                if (!first) sb.append(", ");
                sb.append("@Index(name = \"" + en.getKey() + "\", columnList = \"" + en.getValue().replace("`", "") + "\")");
                first = false;
            }
            sb.append("}");
        }
        sb.append(")\r\n");
        sb.append("public class " + classname + " extends "
            + (superclassname != null && !superclassname.isEmpty() ? superclassname : "BaseEntity") + " {\r\n\r\n");
        boolean idable = false;
        List<StringBuilder> list = new ArrayList<>();
        while (rs.next()) {
            boolean incre = rs.getBoolean("IS_AUTOINCREMENT");
            String column = rs.getString("COLUMN_NAME");
            String type = rs.getString("TYPE_NAME");
            String remark = rs.getString("REMARKS");
            String def = rs.getString("COLUMN_DEF");
            if (!idable) {
                idable = true;
                sb.append("    @Id");
                if (incre) sb.append("\r\n    @GeneratedValue");
            } else if (columns.contains(column)) continue; //跳过被继承的重复字段
            sb.append("\r\n");
            
            int length = 0;
            int precision = 0;
            int scale = 0;
            String ctype = "NULL";
            if ("INT".equalsIgnoreCase(type)) {
                ctype = "int";
            } else if ("BIGINT".equalsIgnoreCase(type)) {
                ctype = "long";
            } else if ("SMALLINT".equalsIgnoreCase(type)) {
                ctype = "short";
            } else if ("FLOAT".equalsIgnoreCase(type)) {
                ctype = "float";
            } else if ("DECIMAL".equalsIgnoreCase(type)) {
                ctype = "float";
                precision = rs.getInt("COLUMN_SIZE");
                scale = rs.getShort("DECIMAL_DIGITS");
            } else if ("DOUBLE".equalsIgnoreCase(type)) {
                ctype = "double";
                precision = rs.getInt("COLUMN_SIZE");
                scale = rs.getShort("DECIMAL_DIGITS");
            } else if ("VARCHAR".equalsIgnoreCase(type)) {
                ctype = "String";
                length = rs.getInt("COLUMN_SIZE");
            } else if (type.contains("TEXT")) {
                ctype = "String";
            } else if (type.contains("BLOB")) {
                ctype = "byte[]";
            }
            sb.append("    @Column(");
            if ("createtime".equals(column)) sb.append("updatable = false, ");
            if(length > 0) sb.append("length = ").append(length).append(", ");
            if(precision > 0) sb.append("precision = ").append(precision).append(", ");
            if(scale > 0) sb.append("scale = ").append(scale).append(", ");            
            sb.append("comment = \"" + remark.replace('"', '\'') + "\")\r\n");
            
            sb.append("    private " + ctype + " " + column);
            if (def != null && !"0".equals(def)) { 
                String d = def.replace('\'', '\"');
                sb.append(" = ").append(d.isEmpty() ? "\"\"" : d);
            } else if ("String".equals(ctype)) {
                sb.append(" = \"\"");
            }
            sb.append(";\r\n");

            char[] chs2 = column.toCharArray();
            chs2[0] = toUpperCase(chs2[0]);
            String sgname = new String(chs2);

            StringBuilder setter = new StringBuilder();
            setter.append("\r\n    public void set" + sgname + "(" + ctype + " " + column + ") {\r\n");
            setter.append("        this." + column + " = " + column + ";\r\n");
            setter.append("    }\r\n");
            list.add(setter);

            StringBuilder getter = new StringBuilder();
            getter.append("\r\n    public " + ctype + " get" + sgname + "() {\r\n");
            getter.append("        return this." + column + ";\r\n");
            getter.append("    }\r\n");
            list.add(getter);
        }
        for (StringBuilder item : list) {
            sb.append(item);
        }
        sb.append("}\r\n");
        return sb.toString();
    }
}
