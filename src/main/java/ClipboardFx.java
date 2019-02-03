import static javafx.application.Platform.runLater;
import static javafx.scene.text.TextAlignment.CENTER;
import static sam.fx.alert.FxAlert.showConfirmDialog;
import static sam.fx.helpers.FxConstants.INSETS_5;
import static sam.io.serilizers.StringReader2.getText;
import static sam.myutils.MyUtilsException.hideError;
import static sam.myutils.MyUtilsException.noError;
import static sam.myutils.MyUtilsPath.selfDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import sam.fx.helpers.FxHBox;
import sam.io.serilizers.StringWriter2;
import sam.nopkg.ModResource;
import sam.nopkg.SimpleModResource;

public class ClipboardFx extends VBox  {
	private final TextArea ta = new TextArea();
	private final ModResource<String> text = new SimpleModResource<String>(this::read, this::write);
	private final Runnable onClose;
	private String not_update;
	private final Path savePath;

	private void write(String s) {
		hideError(() -> StringWriter2.setText(savePath, s), Throwable::printStackTrace);
		System.out.println("write: "+savePath);
	}
	private String read() {
		if(Files.exists(savePath))
			return noError(() -> getText(savePath), Throwable::printStackTrace);
		return null;
	}

	public ClipboardFx(Supplier<HostServices> hostServicesProvider, String uri, Stage parent, Consumer<String> onChange, Runnable onClose) {
		setId("clipboard-fx");
		
		this.savePath = selfDir().resolve(getClass().getName());
		
		Text text = new Text("CLIPBOARD");
		text.setFill(Color.DARKGREEN);
		text.setTextAlignment(CENTER);
		Hyperlink close = new Hyperlink("X");
		close.setTooltip(new Tooltip("close clipboard"));
		
		close.setOnAction(e -> {
			if(!showConfirmDialog("closing clipboard", "Are You Sure?")) 
				e.consume();
			 else
				runLater(onClose);
		});
		HBox top = new HBox(5, text, FxHBox.maxPane(), close);
		
		Hyperlink link = new Hyperlink(uri);
		link.setOnAction(e -> {
			hostServicesProvider.get().showDocument(uri);
			link.setVisited(false);
		});
		getChildren().addAll(top, link, ta);
		setPadding(INSETS_5);
		
		ta.textProperty().addListener(i -> {
			String s = ta.getText();
			if(!Objects.equals(s, not_update)) 
				onChange.accept(s);
		});
		
		this.onClose = onClose;
		ta.setText(this.text.get());
	}

	public void close() {
		text.set(ta.getText());
		hideError(text::close, Throwable::printStackTrace);
		runLater(onClose);
	}
	
	public void setText(String s) {
		Platform.runLater(() -> {
			not_update = s;
			ta.setText(s);
		});
	}
}
