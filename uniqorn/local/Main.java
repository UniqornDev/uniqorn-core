package local;

import aeonics.Plugin;
import aeonics.manager.Lifecycle;
import aeonics.manager.Lifecycle.Phase;
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
	}
	
	private void onLoad()
	{
		Factory.add(new Workspace());
		Factory.add(new Version());
		Factory.add(new Endpoint());
		Factory.add(new uniqorn.storage.File());
		Factory.add(new uniqorn.database.Mariadb());
		Factory.add(new uniqorn.database.Pgsql());
	}
}
