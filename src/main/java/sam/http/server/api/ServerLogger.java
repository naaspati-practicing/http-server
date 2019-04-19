package sam.http.server.api;

import java.util.function.Supplier;

public class ServerLogger {
	public void info(Supplier<String> msg) {}
	public void finer(Supplier<String> msg) { }
	public void fine(Supplier<String> msg) { }
}
