package uniqorn.internal;

import java.util.function.Supplier;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Message;
import aeonics.entity.Registry;
import aeonics.entity.security.User;
import aeonics.manager.Config;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.mcp.Mcp;
import aeonics.template.Parameter;
import aeonics.template.Template;
import aeonics.util.Tuples.Tuple;
import uniqorn.Api;
import uniqorn.Router;
import uniqorn.Workspace;

public class UniqornMcp extends Mcp
{
	public static class Type extends Mcp.Type
	{
		@Override
		public Data tools()
		{
			Data list = Data.list();

			for( Workspace.Type w : Registry.of(Workspace.class) )
			{
				for( Tuple<Entity, Data> e : w.relations("endpoints") )
				{
					if( e.a == null || !e.a.valueOf("enabled").asBool() ) continue;
					uniqorn.Endpoint.Type ep = e.a.cast();
					Api api = ep.api();
					if( api == null ) continue;
					aeonics.http.Endpoint.Rest.Type r = api.api();
					aeonics.http.Endpoint.Template t = api.apitemplate();
					if( r == null || t == null ) continue;

					Data parameters = Data.map();

					for( Parameter p : t.parameters() )
					{
						parameters.put(p.name(), Data.map()
							.put("description", p.description()));
					}

					list.add(Data.map()
						.put("name", r.method() + " " + w.valueOf("prefix").asString() + r.url())
						.put("description", "## Summary: " + t.summary() + "\n## Description: " + t.description() + "\n## Return value: " + t.returns())
						.put("inputSchema", Data.map()
							.put("type", "object")
							.put("properties", parameters)
							.put("required", Data.list())
						)
					);
				}
			}

			return list;
		}

		@Override
		public Data call(String toolName, Data arguments, User.Type user) throws Exception
		{
			int space = toolName.indexOf(' ');
			if( space <= 0 )
				throw new Exception("Unknown tool: " + toolName);

			Manager.of(Logger.class).log(Logger.FINE, Api.class, "MCP tool call {} authenticated as {}", toolName, user.login());
			
			String method = toolName.substring(0, space);
			String path = Manager.of(Config.class).get(Api.class, "prefix").asString() + toolName.substring(space + 1);

			for( aeonics.http.Endpoint.Type e : Registry.of(aeonics.http.Endpoint.class) )
			{
				if( e instanceof Router.Type )
				{
					return e.process(new Message(toolName)
						.content(Data.map().put("method", method).put("path", path).put("get", arguments))
						.user(user == null ? null : user.id())
					);
				}
			}

			throw new Exception("Unknown tool: " + toolName);
		}
	}

	protected Class<? extends Mcp.Type> defaultTarget() { return Type.class; }
	protected Supplier<? extends Mcp.Type> defaultCreator() { return Type::new; }

	@Override
	public Template<? extends Mcp.Type> template()
	{
		return super.template()
			.summary("Uniqorn MCP Provider")
			.description("Exposes Uniqorn workspace endpoints as MCP tools.");
	}
}
