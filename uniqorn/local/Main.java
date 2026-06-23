package local;

import aeonics.Plugin;
import aeonics.manager.Config;
import aeonics.manager.Lifecycle;
import aeonics.manager.Lifecycle.Phase;
import aeonics.manager.Manager;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import uniqorn.Api;
import uniqorn.Endpoint;
import uniqorn.Router;
import uniqorn.Workspace;
import uniqorn.internal.UniqornGitRepo;
import uniqorn.internal.UniqornMcp;

public class Main extends Plugin
{
	public String summary() { return "Uniqorn v1.0.0"; }
	public String description() { return "Uniqorn Core"; }
	public void start()
	{
		Lifecycle.on(Phase.LOAD, this::onLoad);
	}

	private void onLoad()
	{
		Factory.add(new Workspace());
		Factory.add(new Endpoint());
		Factory.add(new Router());
		Factory.add(new UniqornGitRepo());
		Factory.add(new UniqornMcp());
		Factory.add(new uniqorn.storage.File());
		Factory.add(new uniqorn.storage.AWS());
		Factory.add(new uniqorn.database.Mariadb());
		Factory.add(new uniqorn.database.Pgsql());
		Factory.add(new uniqorn.database.Sqlite());

		Manager.of(Config.class).declare(Api.class, new Parameter("policy")
			.summary("Compilation policy")
			.description("The id of the policy that inspects the classes referenced by deployed endpoint code. When set, endpoint deployments are compiled under that policy and rejected if it refuses a referenced class.")
			.format(Parameter.Format.TEXT));
	}
}
