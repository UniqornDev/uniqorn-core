document.addEventListener("DOMContentLoaded", function () {
	const main = document.querySelector("main"); // The scrolling container
	const sections = document.querySelectorAll("h1[id], h2[id]"); // Headers with IDs
	const menuLinks = document.querySelectorAll("nav a[href^='#']"); // Menu links

	let lastScrollTop = main.scrollTop; // Track last scroll position

	function updateMenu()
	{
		let scrollTop = main.scrollTop;
		let mainHeight = main.clientHeight;
		let mainRect = main.getBoundingClientRect();
		let scrollingDown = scrollTop >= lastScrollTop;
		lastScrollTop = scrollTop;

		let highlightedSection = null;

		if (scrollingDown)
		{
			for( let i = 0; i < sections.length; i++ )
			{
				let rect = sections[i].getBoundingClientRect();
				if( rect.top >= mainRect.top )
				{
					if( i == 0 ) highlightedSection = sections[i];
					else if( rect.top < (mainRect.top + mainHeight*0.4) ) highlightedSection = sections[i];
					else highlightedSection = sections[i-1];
					break;
				}
			}
			if( !highlightedSection )
				highlightedSection = sections[sections.length-1];
		}
		else
		{
			for( let i = sections.length-1; i >= 0; i-- )
			{
				let rect = sections[i].getBoundingClientRect();
				if( i == 0 )
				{
					highlightedSection = sections[i];
					break;
				}
				else if( rect.bottom < (mainRect.top + mainHeight*0.6) )
				{
					highlightedSection = sections[i];
					break;
				}
			}
		}

		updateMenuHighlight(highlightedSection.id);
	}

	function updateMenuHighlight(id)
	{
		menuLinks.forEach(link =>
		{
			if( link.getAttribute("href") === "#" + id )
			{
				link.classList.add("current");
				link.scrollIntoView({ block: "nearest", behavior: "smooth" });
			}
			else
				link.classList.remove("current");
		});
	}

	// Attach scroll event listener
	main.addEventListener("scroll", updateMenu);

	// Highlight on page load
	updateMenu();
});
