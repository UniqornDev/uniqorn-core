package local;

import aeonics.Plugin;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.manager.Executor;
import aeonics.manager.Lifecycle;
import aeonics.manager.Lifecycle.Phase;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.template.Factory;
import uniqorn.Endpoint;
import uniqorn.Version;
import uniqorn.Workspace;

public class Main extends Plugin
{
	public String summary() { return "Uniqorn core v0.1"; }
	public String description() { return "Uniqorn core framework"; }
	public void start()
	{
		Lifecycle.on(Phase.LOAD, this::onLoad);
		Lifecycle.after(Phase.RUN, this::recompile);
	}
	
	private void onLoad()
	{
		Factory.add(new Workspace());
		Factory.add(new Version());
		Factory.add(new Endpoint());
		Factory.add(new uniqorn.storage.File());
		Factory.add(new uniqorn.storage.AWS());
		Factory.add(new uniqorn.database.Mariadb());
		Factory.add(new uniqorn.database.Pgsql());
	}
	
	private void recompile()
	{
		// in case we boot from a restore point, then recompile all endpoints
		for( Endpoint.Type e : Registry.of(Endpoint.class) )
		{
			final Endpoint.Type x = e;
			Manager.of(Executor.class).normal(() -> 
			{
				Entity head = x.firstRelation("head");
				if( head == null )
					Manager.of(Logger.class).config(Endpoint.class, "Headless endpoint {}", x.id());
				else
				{
					Manager.of(Logger.class).config(Endpoint.class, "Recompile endpoint {} with version {}", x.id(), head.id());
					x.updateHead();
				}
			});
		}
	}
}
