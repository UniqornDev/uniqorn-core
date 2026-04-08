package uniqorn.internal;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import aeonics.git.Bare;
import aeonics.git.GitRepo;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.template.Template;
import aeonics.util.Tuples.Tuple;
import uniqorn.Api;
import aeonics.util.Tuples.Triple;

public class UniqornGitRepo extends GitRepo
{
	public static class Type extends GitRepo.Type
	{
		/**
		 * Seeds the repository with initial Uniqorn structure if the main branch has no commits (zero SHA).
		 * Safe to call at any time, does nothing if the branch already has commits.
		 */
		public void seed()
		{
			String sha = store().getString(root() + "/refs/heads/" + branch());
			if( sha == null || !"0000000000000000000000000000000000000000".equals(sha.trim()) )
				return;

			Bare.createFile(store(), root(), "README.md",
				("```\n"
				+ "888     888 888b    888 8888888 .d88888b.   .d88888b.  8888888b.  888b    888 \n"
				+ "888     888 8888b   888   888  d88P\" \"Y88b d88P\" \"Y88b 888   Y88b 8888b   888 \n"
				+ "888     888 88888b  888   888  888     888 888     888 888    888 88888b  888 \n"
				+ "888     888 888Y88b 888   888  888     888 888     888 888   d88P 888Y88b 888 \n"
				+ "888     888 888 Y88b888   888  888     888 888     888 8888888P\"  888 Y88b888 \n"
				+ "888     888 888  Y88888   888  888 Y8b 888 888     888 888 T88b   888  Y88888 \n"
				+ "Y88b. .d88P 888   Y8888   888  Y88b.Y8b88P Y88b. .d88P 888  T88b  888   Y8888 \n"
				+ " \"Y88888P\"  888    Y888 8888888 \"Y888888\"   \"Y88888P\"  888   T88b 888    Y888 \n"
				+ "                                      Y8b                                     \n"
				+ "```\n"
				+ "\n"
				+ "# Uniqorn Git Integration\n"
				+ "\n"
				+ "This repository is a **bare Git repository** managed by the Uniqorn server.\n"
				+ "Push Java source files here and they are instantly compiled and deployed as live REST endpoints.\n"
				+ "When you push Java source files here, they are **automatically compiled and published as live REST endpoints**, no extra build steps required.\n"
				+ "\n"
				+ "## How It Works\n"
				+ "\n"
				+ "1. On `git push`, the server scans incoming changes.\n"
				+ "2. Valid Java files are compiled.\n"
				+ "3. Compiled code is deployed as a REST API endpoint within seconds.\n"
				+ "\n"
				+ "## Rules for File Structure\n"
				+ "\n"
				+ "- **Java sources must be in**: `/src/[workspace]/[name].java`\n"
				+ "- `[workspace]` : acts as the workspace name and **URL prefix** for the endpoint. It cannot contain special characters or subfolders.\n"
				+ "- `[name].java` : any valid Java file name containing the API implementation. The file name is unrelated to the endpoint URL.\n"
				+ "- **Web assets must be in**: `/www/[path]`\n"
				+ "- Files in `/www/` are extracted and served as static content on push.\n"
				+ "- Deleting a file from `/www/` will remove it from the server.\n"
				+ "- Other files outside `src/` and `www/` are allowed but **will not be processed**.\n"
				+ "\n"
				+ "## Notes\n"
				+ "\n"
				+ "- Any updates to a Java file will trigger a recompile and redeploy on push.\n"
				+ "- Deleting a Java file will unpublish the endpoint.\n"
				+ "- Status and compilation errors will be reported at each push.\n"
				).getBytes(StandardCharsets.ISO_8859_1),
				null, "Initialize repository", null);
			Bare.createFile(store(), root(), "src/.gitkeep",
				new byte[0],
				null, "Add src folder", null);
			Bare.createFile(store(), root(), "www/index.html",
				("<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
				+ "<title>Uniqorn</title>\n"
				+ "<style>html,body{margin:0;height:100%;display:flex;align-items:center;justify-content:center;background:#f5f5f7;font-family:system-ui,sans-serif}"
				+ "svg{width:120px;height:auto}</style>\n"
				+ "</head>\n<body>\n"
				+ "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1653 1787\">"
				+ "<g id=\"hair\">"
				+ "<path d=\"m1437 764 186.82 132.05 10.3094 358.6382z\" fill=\"#bac960\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M1165.89 244 1483.7794 496.49418 1107.31 389.906 970.597 266.73766Z\" fill=\"#ce3a54\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M1200 452 1511.78 537.391 1653 856 1382.7351 657.82633Z\" fill=\"#e19a44\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M806 154 1078 410.091 847.21 467Z\" fill=\"#e17066\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M566 195 778.456 285.64268 802 440 605.486 323.899Z\" fill=\"#a41f3f\" fill-rule=\"evenodd\"/>"
				+ "</g>"
				+ "<g id=\"horn\">"
				+ "<path d=\"M580.807 350 756 456.217 676.2 587.157 555.124 654 490 575.253 571.634 437.904Z\" fill=\"#c33f74\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M407.952 241 556 335.313 538.636 426.88 467.354 545 365 431.458Z\" fill=\"#e19a44\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M274.142 157 383 227.559 344.58 398 222 265.129Z\" fill=\"#ebbd45\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M0 0 249 147.992 203.228 243Z\" fill=\"#c6cc68\" fill-rule=\"evenodd\"/>"
				+ "</g>"
				+ "<g id=\"neck\">"
				+ "<path d=\"M1101 1176.81 1269.95 1629 910 1787l17.84-549.89Z\" fill=\"#9A9EC7\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M1365.14 697 1585 1253.28 1454 1406.34 1190.28 1016Z\" fill=\"#d0d2e3\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M1190.28 1016 1101 1176.81 1269.95 1629 1454 1406.34Z\" fill=\"#d0d2e3\" fill-rule=\"evenodd\"/>"
				+ "</g>"
				+ "<g id=\"face\">"
				+ "<path d=\"M854.2694 501.14977 1142.2301 430.06468 1333 656.869 1199.59 900.1 524.309 1278 327.638 1180.43 274 1107.6 546.314 689.85 777.9499 632.9683Z\" fill=\"#d0d2e3\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M1199.59 900.1 1067.95 1150.5 618 1321 524.309 1278Z\" fill=\"#9a9ec7\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"m274 1107.6-46 76.96 65.684 178.58L329 1385.56 361.77484 1197.3655 327.638 1180.43Z\" fill=\"#aeb1d0\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M618 1321 591.853 1424.02 445.976 1446 329 1385.56 361.78667 1197.2976 524.309 1278Z\" fill=\"#7c81b6\" fill-rule=\"evenodd\"/>"
				+ "<path d=\"M634 830 693.26 750.96 803 729 752.52 812.43Z\" fill=\"#454a97\" fill-rule=\"evenodd\"/>"
				+ "</g>"
				+ "</svg>\n"
				+ "</body>\n</html>\n"
				).getBytes(StandardCharsets.UTF_8),
				null, "Add default page", null);
		}

		@Override
		public String onPush(
			Map<String, Tuple<String, String>> refs,
			Set<Triple<String, byte[], byte[]>> affectedFiles)
		{
			Manager.of(Logger.class).log(Logger.FINER, Api.class, "GIT push - {} affected files", affectedFiles.size());
			
			Tuple<String, String> main = refs.get("refs/heads/" + branch());
			if( main == null )
				return "No changes detected on '" + branch() + "' branch.\nNothing to deploy.";
			return GitSync.apply(affectedFiles);
		}
	}

	protected Class<? extends GitRepo.Type> defaultTarget() { return Type.class; }
	protected Supplier<? extends GitRepo.Type> defaultCreator() { return Type::new; }

	@Override
	public Template<? extends GitRepo.Type> template()
	{
		return super.template()
			.summary("Uniqorn Git Repository")
			.description("A Git repository that auto-deploys Java endpoints on push.");
	}
}
