package uniqorn.storage;

import java.util.function.Supplier;

import aeonics.entity.Storage;

public class File extends Storage.File
{
	public static class Type extends Storage.File.Type { }
	
	protected Class<? extends File.Type> defaultTarget() { return File.Type.class; }
	protected Supplier<? extends File.Type> defaultCreator() { return File.Type::new; }
}
