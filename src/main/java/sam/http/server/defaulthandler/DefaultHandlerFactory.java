package sam.http.server.defaulthandler;

import java.util.Objects;

import javax.inject.Named;

import org.codejargon.feather.Provides;

import sam.http.server.api.DocRootFactory;
import sam.http.server.api.IHandlerFactory;
import sam.http.server.api.ServerLogger;
import sam.http.server.api.Utils;

public class DefaultHandlerFactory implements IHandlerFactory {
	private static volatile DefaultHandler instance;
	private static final DocRootFactory[] MARKER = new DocRootFactory[0];

	@Override
	public void create(ServerLogger logger) {
		create(logger, MARKER);
	}

	public void create(ServerLogger logger, DocRootFactory[] factories) {
		Objects.requireNonNull(factories);
		
		if(factories == MARKER)
			factories = Utils.serviceLoaded(DocRootFactory.class);
		
		instance = new DefaultHandler(logger, factories);
	}

	@Provides
	@Named("default-handler")
	@Override
	public DefaultHandler get() {
		return instance;
	}
}
