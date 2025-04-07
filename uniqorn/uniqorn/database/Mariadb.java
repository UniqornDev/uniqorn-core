package uniqorn.database;

import java.util.function.Supplier;

import aeonics.entity.Database;
import aeonics.template.Parameter;
import aeonics.template.Template;

public class Mariadb extends Database
{
	public static class Type extends Database.Type
	{
		public Type()
		{
			this.onCreate().then((data, entity) ->
			{
				data = data.get("parameters");
				
				entity.parameter("size", 10);
				entity.parameter("driver", "org.mariadb.jdbc.Driver");
				entity.parameter("jdbc", "jdbc:mariadb://" + data.asString("host") + ":" + data.asString("port") + "/" + data.asString("database")
					+ (data.asBool("ssl") ? "?sslMode=trust" : ""));
				// tcpKeepAlive=true by default
				// sslMode=disable by default
				
				entity.<Database.Type>cast().refreshPoolSize();
			});
		}
	}
	
	protected Class<? extends Database.Type> defaultTarget() { return Mariadb.Type.class; }
	protected Supplier<? extends Database.Type> defaultCreator() { return Mariadb.Type::new; }
	
	@Override
	public Template<? extends Database.Type> template()
	{
		return super.template()
			.removeParameter("size")
			.removeParameter("driver")
			.removeParameter("jdbc")
			.add(new Parameter("port")
				.summary("Port")
				.description("The database port.")
				.format(Parameter.Format.NUMBER)
				.rule(Parameter.Rule.INTEGER)
				.optional(false))
			.add(new Parameter("host")
				.summary("Host")
				.description("The database host.")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("database")
				.summary("Database")
				.description("The name of the database.")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("ssl")
				.summary("SSL")
				.description("Whether or not to use SSL.")
				.format(Parameter.Format.BOOLEAN)
				.rule(Parameter.Rule.BOOLEAN)
				.optional(false)
				.defaultValue(false))
			;
	}
}
