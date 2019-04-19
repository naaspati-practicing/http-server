package sam.http.clipboard;

import org.codejargon.feather.Provides;

import sam.http.server.api.IHandlerFactory;
import sam.http.server.api.ServerLogger;

public class ClipboardHandlerFac implements IHandlerFactory {
	private static volatile ClipboardHandler instance;

	@Override
	public void create(ServerLogger logger) {
		ClipboardHandler instance = new ClipboardHandler(logger);
		ClipboardHandlerFac.instance = instance;
	}
	
	@Provides
	@Override
	public ClipboardHandler get() {
		return instance;
	}

}
