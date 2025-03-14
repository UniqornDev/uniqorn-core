package uniqorn;

import java.util.function.Supplier;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.template.Item;
import aeonics.template.Parameter;
import aeonics.template.Template;
import aeonics.util.StringUtils;

public class Version extends Item<Version.Type>
{
	public static class Type extends Entity
	{
		/**
		 * Hardcoded category to the {@link Version} class
		 */
		@Override
		public final String category() { return StringUtils.toLowerCase(Version.class); }
		
		long date = 0;
		public long date() { return date; }
		
		@Override
		public Data export()
		{
			return super.export().put("date", date());
		}
		
		@Override
		public Data snapshot()
		{
			return super.snapshot().put("date", date());
		}
	}
	
	protected Class<? extends Version.Type> defaultTarget() { return Version.Type.class; }
	protected Supplier<? extends Version.Type> defaultCreator() { return Version.Type::new; }
	protected Class<? extends Version> category() { return Version.class; }

	@Override
	public Template<? extends Version.Type> template()
	{
		return super.template()
			.summary("Version")
			.description("A version is composed of a name and a source code.")
			.add(new Parameter("code")
				.summary("Code")
				.description("Version source code.")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(1024*512))
			.onCreate((data, entity) ->
			{
				if( data.isNumber("date") ) entity.date = data.asLong("date");
				else entity.date = System.currentTimeMillis();
			})
			;
	}
}
