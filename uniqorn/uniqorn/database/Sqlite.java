package uniqorn.database;

import java.util.function.Supplier;

import aeonics.entity.Database;
import aeonics.template.Parameter;
import aeonics.template.Template;

public class Sqlite extends Database
{
	public static class Type extends Database.Type
	{
		public Type()
		{
			this.onCreate().then((data, entity) ->
			{
				data = data.get("parameters");
				
				entity.parameter("size", 10);
				entity.parameter("driver", "org.sqlite.JDBC");
				entity.parameter("jdbc", "jdbc:sqlite:" + data.asString("path"));
				
				entity.<Database.Type>cast().refreshPoolSize();
			});
		}
	}
	
	protected Class<? extends Database.Type> defaultTarget() { return Sqlite.Type.class; }
	protected Supplier<? extends Database.Type> defaultCreator() { return Sqlite.Type::new; }
	
	@Override
	public Template<? extends Database.Type> template()
	{
		return super.template()
			.removeParameter("size")
			.removeParameter("driver")
			.removeParameter("jdbc")
			.add(new Parameter("path")
				.summary("Path")
				.description("The absolute path to the database file.")
				.format(Parameter.Format.TEXT)
				.optional(false))
			;
	}
}
