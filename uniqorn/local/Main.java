package local;

import aeonics.Plugin;
import aeonics.manager.*;
import aeonics.manager.Lifecycle.Phase;

public class Main extends Plugin
{
	public String summary() { return "Uniqorn v0.1"; }
	public String description() { return "Uniqorn REST API public"; }
	
	public void start()
	{
		Lifecycle.on(Phase.RUN, this::onRun);
	}
	
	private void onRun()
	{
		Endpoints.register();
	}
}
