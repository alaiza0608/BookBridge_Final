package app;

import dao.ConnectionFactory;
import dao.LibraryDao;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import service.LibraryService;
import ui.BookBridgeFrame;

public class BookBridgeApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.initializeSchemaFromFile("schema.sql");
                LibraryDao dao = new LibraryDao(factory);
                LibraryService service = new LibraryService(dao);
                new BookBridgeFrame(service).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Startup database initialization failed:\n" + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
