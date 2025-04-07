module uniqorn
{
	requires aeonics.boot;
	requires transitive aeonics.core;
	requires transitive aeonics.http;
	
	exports uniqorn;
	exports uniqorn.storage;
	exports uniqorn.database;
	
	provides aeonics.Plugin with local.Main;
}
