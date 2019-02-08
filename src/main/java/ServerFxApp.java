
// import sam.fx.helpers.FxConstants;
import static javafx.application.Platform.runLater;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.util.function.Supplier;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.http.clipboardserver.ClipboardServer;
import sam.http.server.Server;
import sam.http.server.extra.ServerLogger;
import sam.io.fileutils.FileOpenerNE;
import sam.myutils.MyUtilsPath;
import sam.nopkg.ModResource;
import sam.nopkg.ModResourceSavedAsString;
import sam.reference.WeakAndLazy;

public class ServerFxApp extends Application implements ServerLogger {
	
	private static final Path SELF_DIR = MyUtilsPath.selfDir();
	
	private Server server;
	private Stage stage;
	@FXML private TextArea center;
	@FXML private Hyperlink top;
	@FXML private Hyperlink uri_browse;
	@FXML private Button clipboard;
	@FXML private VBox viewRoot;

	@Override
	public void start(Stage stage) throws Exception {
		server = new Server(8080, this);
		server.start();

		stage.setTitle("Http-Server");
		FxFxml.load(this, stage, this);
		stage.getScene().getStylesheets().add("styles.css");
		top.setVisible(false);

		FileOpenerNE.setErrorHandler((file, e) -> e.printStackTrace());
		this.stage = stage;
		FxPopupShop.setParent(stage);
		FxAlert.setParent(stage);
		stage.show();
		
		Platform.runLater(() -> uri_browse.setText(server.getBaseUri()));
	}

	private final ModResource<File> previousVisit = new ModResourceSavedAsString<>(SELF_DIR.resolve("previousVisit"), File::new);
	private final WeakAndLazy<DirectoryChooser> wdc = new WeakAndLazy<>(DirectoryChooser::new);
	private final WeakAndLazy<FileChooser> wfc = new WeakAndLazy<>(() -> {
		FileChooser fc = new FileChooser();
		fc.getExtensionFilters().add(new ExtensionFilter("zip file", "*.zip"));
		return fc;
	});
	
	private class ClipboardWrap {
		final ClipboardFx fx;
		final ClipboardServer cerver;	
		
		public ClipboardWrap() throws UnsupportedEncodingException, IOException {
			cerver = new ClipboardServer(this::setFxText);
			fx = new ClipboardFx(() -> getHostServices(), server.getBaseUri().concat(cerver.getPath()), stage, cerver::setText, this::onClose);
		}
		private void onClose() {
			clipboard.setDisable(false);
			viewRoot.getChildren().remove(fx);
			stage.sizeToScene();
			server.remove(cerver);
		}
		private void setFxText(String s) {
			fx.setText(s);
		}
		public void open() {
			clipboard.setDisable(true);
			viewRoot.getChildren().add(fx);
			stage.sizeToScene();
			server.add(cerver);
		}
	}
	
	@FXML
	private void openBaseUri(Event e) {
		getHostServices().showDocument(server.getBaseUri());
		uri_browse.setVisited(false);
	}
	private ClipboardWrap clipboardWrap;
	
	@FXML
	private void openClipboard(Event e) {
		if(clipboardWrap == null) {
			try {
				clipboardWrap = new ClipboardWrap();
			} catch (IOException e1) {
				FxAlert.showErrorDialog(null, "error", e1);
				return;
			}
		} 
		clipboardWrap.open();
	}

	@FXML
	private void selectDir(Event e) {
		DirectoryChooser dc = wdc.get();

		if(previousVisit.get() != null)
			dc.setInitialDirectory(previousVisit.get());

		setRoot(dc.showDialog(stage));
	}

	@FXML
	private void selectZip(Event e) {
		FileChooser fc = wfc.get();
		if(previousVisit.get() != null)
			fc.setInitialDirectory(previousVisit.get());

		setRoot(fc.showOpenDialog(stage));
	}

	private void setRoot(File file) {
		if(file == null)
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			try {
				server.setRoot(file);
				previousVisit.set(file.getParentFile());
				top.setTooltip(new Tooltip(file.toString()));
				top.setText(file.getName());
				top.setVisited(false);
				top.setUserData(file);
				top.setVisible(true);
			} catch (Exception e) {
				FxAlert.showErrorDialog(file, "failed to change root", e);
			}
		}
	}

	@FXML
	private void openRootPath(Event e) {
		File file = (File) top.getUserData();
		if(file == null)
			return;

		top.setVisited(false);
		if(!file.exists())
			center.appendText("file/dir not found: "+file+"\n");
		if(file.isDirectory())
			FileOpenerNE.openFile(file);
		else
			FileOpenerNE.openFileLocationInExplorer(file);
	}

	@Override
	public void stop() throws Exception {
		if(server != null)
			server.stop();
		if(clipboardWrap != null)
			clipboardWrap.fx.close();
		previousVisit.close();
		System.out.println("server shutdown");
		super.stop();
	}

	@Override
	public void fine(Supplier<String> msg) {
		runLater(() -> center.appendText(msg.get()+"\n"));
	}

	@Override public void finer(Supplier<String> msg) { }

	@Override
	public void info(Supplier<String> msg) {
		runLater(() -> center.appendText(msg.get()+"\n"));
	}
}
