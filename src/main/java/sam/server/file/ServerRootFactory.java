package sam.server.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import sam.http.server.extra.ServerLogger;

public interface ServerRootFactory {
	public static DocRoot of(Path path, ServerLogger logger) throws IOException {
		if(Files.notExists(path))
			throw new IOException("file/dir not found: "+path);
		
		if(Files.isDirectory(path))
			return new DirDocRoot(logger, path);
		else if(path.getFileName().toString().toLowerCase().endsWith(".zip")) 
			return new ZipDocRoot(logger, path);
		
		throw new IOException("roothandler not found for: "+path);
	}
	
}
