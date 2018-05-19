import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.*;

// import org.postgresql.PGConnection;
/**
 *
 * @author postgresqltutorial.com
 */
public class App {

    /**
     * Connect to the PostgreSQL database
     *
     * @return a Connection object
     */
    public Connection connect() throws SQLException {
        // Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(System.getenv("JDBC_DATABASE_URL"));
        System.out.println("Connected to the PostgreSQL server successfully.");

        return conn;
    }

    public void truncateTable(Connection conn) {
        try {
            PreparedStatement ps = conn.prepareStatement("TRUNCATE TABLE world_files;");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void dropTable(Connection conn) {
        try {
            PreparedStatement ps = conn.prepareStatement("DROP TABLE world_files;");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void createTable(Connection conn) {
        try {
            PreparedStatement ps = conn.prepareStatement("CREATE TABLE world_files (file_name text, file_contents bytea);");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void persistFile(Connection conn, File file) {
        try {   
            // All LargeObject API calls must be within a transaction block
            conn.setAutoCommit(false);

            // Now open the file
            FileInputStream fis = new FileInputStream(file);

            // Now insert the row into imageslo
            PreparedStatement ps = conn.prepareStatement("INSERT INTO world_files VALUES (?, ?)");
            ps.setString(1, file.getPath());
            ps.setBinaryStream(2, fis, file.length());
            ps.executeUpdate();

            // Finally, clean up and commit the transaction.
            fis.close();
            ps.close();
            conn.commit();
        } catch (FileNotFoundException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void restoreFiles(Connection conn) {
        try {
            // All LargeObject API calls must be within a transaction block
            conn.setAutoCommit(false);

            PreparedStatement ps = conn.prepareStatement("SELECT file_name, file_contents FROM world_files");
            ResultSet rs = ps.executeQuery();
            if (rs != null) {
                while (rs.next()) {
                    // Open the large object for reading
                    String filePath = rs.getString(1);
                    try {  
                        InputStream initialStream = rs.getBinaryStream(2);

                        byte[] buffer = new byte[initialStream.available()];
                        initialStream.read(buffer);

                        System.out.println("Restoring file: " + filePath);
                        File targetFile = new File(filePath);
                        File parent = targetFile.getParentFile();
                        if (!parent.exists() && !parent.mkdirs()) {
                            throw new IllegalStateException("Couldn't create dir: " + parent);
                        }
                        OutputStream outStream = new FileOutputStream(targetFile);
                        outStream.write(buffer);

                        // // Close the object
                        initialStream.close();
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                }
                rs.close();
            }
            ps.close();

            // Finally, commit the transaction.
            conn.commit();
        } catch (SQLException e) {
            System.out.println("SQLException");
            System.out.println(e.getMessage());
        }

    }

    public void walk( Connection conn, String path ) {

        File root = new File( path );
        File[] list = root.listFiles();

        if (list == null) return;

        for ( File f : list ) {
            if ( f.isDirectory() ) {
                System.out.println( "Dir:" + f.getAbsoluteFile() );
                walk( conn, f.getAbsolutePath() );
            }
            else {
                File filePath = f.getAbsoluteFile();
                System.out.println("Persisting file: " + filePath);
                this.persistFile(conn, filePath);
            }
        }
    }
 
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws SQLException{
        System.out.println("Starting directory replication");
        App app = new App();
        Connection conn = null;
        try {
            conn = app.connect();
            switch (args[0]) {
                case "drop":
                    System.out.println("============== Dropping =============");
                    app.dropTable(conn);
                    System.out.println("============== Dropped =============");
                    break;
                case "trunc":
                    System.out.println("============== Truncating =============");
                    app.truncateTable(conn);
                    System.out.println("============== Truncated =============");
                    break;
                case "persist":
                    app.createTable(conn);
                    app.truncateTable(conn);
                    app.walk(conn, "world");
                    break;
                case "restore":
                    app.restoreFiles(conn);
                    break;
                default:
                    System.out.println("No arguments specified");
                    break;
            }
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
            }
        } 

    }
}

