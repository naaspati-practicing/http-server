package sam.http.server.extra;

import java.util.function.Supplier;

public interface ServerLogger {
	void fine(Supplier<String> msg);
	void finer(Supplier<String> msg);
	void info(Supplier<String> msg);

}
