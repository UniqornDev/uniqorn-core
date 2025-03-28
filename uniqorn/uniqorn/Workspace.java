package uniqorn;

import java.io.Closeable;
import java.util.function.Supplier;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.template.Item;
import aeonics.template.Parameter;
import aeonics.template.Relationship;
import aeonics.template.Template;
import aeonics.util.StringUtils;
import aeonics.util.Tuples.Tuple;

public class Workspace extends Item<Workspace.Type>
{
	public static class Type extends Entity implements Closeable
	{
		/**
		 * Hardcoded category to the {@link Workspace} class
		 */
		@Override
		public final String category() { return StringUtils.toLowerCase(Workspace.class); }
		
		public void close()
		{
			// delete cascade
			for( Tuple<Entity, Data> e : relations("endpoints") )
				Registry.of(Endpoint.class).remove(e.a);
		}
	}
	
	protected Class<? extends Workspace.Type> defaultTarget() { return Workspace.Type.class; }
	protected Supplier<? extends Workspace.Type> defaultCreator() { return Workspace.Type::new; }
	protected Class<? extends Workspace> category() { return Workspace.class; }

	@Override
	public Template<? extends Workspace.Type> template()
	{
		return super.template()
			.summary("Workspace")
			.description("A workspace is a named collection of APIs. A workspace may impose a URL prefix to all its APIs.")
			.add(new Parameter("prefix")
				.summary("Prefix")
				.description("Optional url prefix for all endpoints in this workspace.")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50)
				.defaultValue(""))
			.add(new Relationship("endpoints")
				.category(Endpoint.class)
				.summary("Endpoints")
				.description("The endpoints in this workspace."))
			;
	}
}
