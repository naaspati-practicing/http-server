package sam.http.server.api;

public interface IHandlerFactory {
	public IHandler get(); 
	
	default void create() {
		create(null);
	}
	public void create(ServerLogger logger);
}
