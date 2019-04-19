
// import sam.fx.helpers.FxConstants;
import static javafx.application.Platform.runLater;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
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
import sam.collection.ArraysUtils;
import sam.di.FeatherInjector;
import sam.di.Injector;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxFxml;
import sam.fx.helpers.FxUtils;
import sam.fx.popup.FxPopupShop;
import sam.http.clipboard.ClipboardFx;
import sam.http.server.Server;
import sam.http.server.api.DocRootHandler;
import sam.http.server.api.IHandler;
import sam.http.server.api.IHandlerFactory;
import sam.http.server.api.ServerLogger;
import sam.http.server.api.Utils;
import sam.io.fileutils.FileOpenerNE;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.nopkg.SavedAsStringResource;
import sam.nopkg.SavedResource;
import sam.reference.WeakAndLazy;

public class ServerFxApp extends Application {

	private static final Path SELF_DIR = MyUtilsPath.selfDir();

	private Server server;
	private Stage stage;
	@FXML private TextArea center;
	@FXML private Hyperlink top;
	@FXML private Hyperlink uri_browse;
	@FXML private Button clipboardBtn;
	@FXML private VBox viewRoot;
	private final DocRootHandler handler = Injector.getInstance().instance(DocRootHandler.class, "default-handler");

	@Override
	public void start(Stage stage) throws Exception {
		try {
			IHandlerFactory[] handler = Utils.serviceLoaded(IHandlerFactory.class);
			
			if(Checker.isEmpty(handler)) {
				error("no handlers specified", null);
				return;
			}
			
			ServerLogger logger = logger();

			for (IHandlerFactory h : handler) 
				h.create(logger);

			List<Object> modules = FeatherInjector.prepare_modules((Object[])handler);
			if(modules.getClass() != ArrayList.class)
				modules = new ArrayList<>(modules);
			
			modules.add(this);
			FeatherInjector injector = new FeatherInjector(FeatherInjector.default_mappings(), modules);
			Injector.init(injector);

			server = new Server(8080, logger, ArraysUtils.map(handler, new IHandler[handler.length], h -> Objects.requireNonNull(h.get())));
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
		} catch (Throwable e) {
			error(null, e);
		}
	}

	private void error(String s, Throwable e) {
		FxUtils.setErrorTa(stage, "failed to open app", s, e);
		stage.sizeToScene();
		stage.show();
	}

	private ServerLogger logger() {
		return new ServerLogger() {
			@Override
			public void fine(Supplier<String> msg) {
				runLater(() -> center.appendText(msg.get()+"\n"));
			}

			@Override public void finer(Supplier<String> msg) { }

			@Override
			public void info(Supplier<String> msg) {
				runLater(() -> center.appendText(msg.get()+"\n"));
			}
		};
	}

	private final SavedResource<File> previousVisit = new SavedAsStringResource<>(SELF_DIR.resolve("previousVisit"), File::new);
	private final WeakAndLazy<DirectoryChooser> wdc = new WeakAndLazy<>(DirectoryChooser::new);
	private final WeakAndLazy<FileChooser> wfc = new WeakAndLazy<>(() -> {
		FileChooser fc = new FileChooser();
		fc.getExtensionFilters().add(new ExtensionFilter("zip file", "*.zip"));
		return fc;
	});

	private class ClipboardWrap extends ClipboardFx implements Consumer<String> {
		@Override
		public void close() {
			super.close();

			clipboardBtn.setDisable(false);
			viewRoot.getChildren().remove(this);
			stage.sizeToScene();
		}

		@Override
		public void accept(String s) {
			setText(s);
		}
		public void open() {
			clipboardBtn.setDisable(true);
			viewRoot.getChildren().add(this);
			stage.sizeToScene();
			setOnChange(this);
			this.start();
		}
		@Override
		protected void showDocument(String uri) {
			getHostServices().showDocument(uri);
		}
		@Override
		protected String uri(String path) {
			return server.getBaseUri().concat(path);
		}
	}

	@FXML
	private void openBaseUri(Event e) {
		getHostServices().showDocument(server.getBaseUri());
		uri_browse.setVisited(false);
	}

	private ClipboardWrap clipboard;

	@FXML
	private void openClipboard(Event e) {
		if(clipboard == null) 
			clipboard = new ClipboardWrap();
		clipboard.open();
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
				handler.setDocRoot(file.toPath());
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
		if(clipboard != null)
			clipboard.close();
		previousVisit.close();
		System.out.println("server shutdown");
		super.stop();
	}
}
