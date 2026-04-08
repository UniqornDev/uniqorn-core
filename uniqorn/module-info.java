module uniqorn
{
	requires aeonics.boot;
	requires transitive aeonics.core;
	requires transitive aeonics.http;
	requires transitive aeonics.git;
	requires transitive aeonics.mcp;

	exports uniqorn;
	exports uniqorn.internal;
	exports uniqorn.storage;
	exports uniqorn.database;

	provides aeonics.Plugin with local.Main;
}
