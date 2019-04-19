package sam.http.clipboard;
import static javafx.application.Platform.runLater;
import static javafx.scene.text.TextAlignment.CENTER;
import static sam.fx.alert.FxAlert.showConfirmDialog;
import static sam.fx.helpers.FxConstants.INSETS_5;
import static sam.myutils.MyUtilsException.hideError;
import static sam.myutils.MyUtilsPath.selfDir;

import java.util.function.Consumer;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import sam.di.Injector;
import sam.fx.helpers.FxHBox;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.SavedAsStringResource;
import sam.nopkg.SavedResource;
import sam.thread.DelayedActionThread;

public abstract class ClipboardFx extends VBox implements InvalidationListener  {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{
		singleton.init();
	}

	private final TextArea ta = new TextArea();
	private final SavedResource<String> text = new SavedAsStringResource<>(selfDir().resolve(getClass().getName()), s -> s);
	private DelayedActionThread<String> actionThread = new DelayedActionThread<>(300, this::notifyTextChange);
	private volatile int self_mod;
	private final ClipboardHandler clipboardHandler = Injector.getInstance().instance(ClipboardHandler.class);

	protected abstract void showDocument(String uri);
	protected void notifyTextChange(String s) {
		text.set(s);
	}
	protected void setText(String s) {
		self_mod++;
		ta.setText(s);
		text.set(s);
	}
	protected abstract String uri(String path);

	public void setOnChange(Consumer<String> consumer) {
		clipboardHandler.setOnChange(consumer);
	}

	public ClipboardFx() {
		setId("clipboard-fx");

		Text text = new Text("CLIPBOARD");
		text.setFill(Color.DARKGREEN);
		text.setTextAlignment(CENTER);
		Hyperlink close = new Hyperlink("X");
		close.setTooltip(new Tooltip("close clipboard"));

		close.setOnAction(e -> {
			if(!showConfirmDialog("closing clipboard", "Are You Sure?")) 
				e.consume();
			else
				runLater(this::close);
		});
		HBox top = new HBox(5, text, FxHBox.maxPane(), close);

		String uri = uri(clipboardHandler.getPath());
		Hyperlink link = new Hyperlink(uri);

		link.setOnAction(e -> {
			showDocument(uri);
			link.setVisited(false);
		});

		getChildren().addAll(top, link, ta);
		setPadding(INSETS_5);
	}

	protected void start() {
		ta.textProperty().addListener(this);
		ta.setText(this.text.get());
	}
	protected void stop() {
		ta.textProperty().removeListener(this);
	}
	@Override
	public void invalidated(Observable observable) {
		if(self_mod == 0)
			actionThread.queue(ta.getText());
		else
			self_mod--;
	}
	public void close() {
		clipboardHandler.setOnChange(null);
		stop();
		actionThread.stop();
		text.set(ta.getText());

		hideError(text::close, Throwable::printStackTrace);
	}
}
