module uniqorn
{
	requires aeonics.boot;
	requires transitive aeonics.core;
	requires transitive aeonics.http;
	
	exports uniqorn;
	
	provides aeonics.Plugin with local.Main;
}
