function fetchUptime()
{
	fetch('/api/status', {method: 'GET'})
		.then(response => response.json())
		.then(json =>
		{
			for( const [key, value] of Object.entries(json) )
			{
				const div = document.createElement('div');
				div.className = 'entry';
				const h = document.createElement('h3');
				h.textContent = key;
				div.append(h);
				
				const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
				svg.setAttribute('preserveAspectRatio', 'none');
				svg.setAttribute('viewBox', '0 0 450 32');
				svg.classList.add('bars');
				svg.addEventListener('mouseover', uptimeTip);
				svg.addEventListener('mouseout', () =>
				{
					let t = document.getElementById('tooltip');
					if( t ) t.remove();
				});

				for( let i = 0; i < value.length; i++ )
				{
					const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
					rect.setAttribute('x', i * 5);
					rect.setAttribute('y', 0);
					rect.setAttribute('width', 3);
					rect.setAttribute('height', '32');
					rect.setAttribute('data-up', value[i]);
					rect.setAttribute('data-day', i - 90);
				
					if (i == value.length - 1)
					{
						const now = new Date();
						var max = now.getUTCHours() * 60 + now.getUTCMinutes();
						if( value[i] < max ) rect.classList.add('o');
						else rect.classList.add('g');
					}
					else if (value[i] < 0) rect.classList.add('n');
					else if (value[i] < 1260) rect.classList.add('r');
					else if (value[i] < 1440) rect.classList.add('o');
					else rect.classList.add('g');

					svg.append(rect);
				}
				
				div.append(svg);
				document.getElementById('uptime').append(div);
			}
		})
		.catch(error =>
		{
			const p = document.createElement('p');
			p.textContent = 'Could not fetch status information';
			document.getElementById('uptime').append(p);
			console.log(error);
		});
}

function uptimeTip(e)
{
	if( e.target.nodeName != 'rect' ) return;
	let t = document.getElementById('tooltip');
	if( !t )
	{
		t = document.createElement('aside');
		t.id = 'tooltip';
		document.body.append(t);
	}
	
	const rect = e.target.getBoundingClientRect();
	t.style.left = (rect.left + rect.width / 2 + window.scrollX) + 'px';
	t.style.top = (rect.bottom + 4 + window.scrollY) + 'px';
	
	var max = 1440;
	if( !e.target.nextSibling )
	{
		// last item
		const now = new Date();
		max = now.getUTCHours() * 60 + now.getUTCMinutes();
	}
	
	var up = parseInt(e.target.dataset.up);
	var down = max - up;
	var day = parseInt(e.target.dataset.day);
	var date = new Date();
	date.setDate(date.getDate() + day);
	date = date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
	
	if( up < 0 )
	{
		t.innerHTML = "<em>" + date + "</em>"
			+ "No data for this day";
	}
	else
	{
		var upHours = Math.floor(up / 60);
		var upMinutes = up % 60;
		var upPercent = Math.max(0, Math.min(100, ((up / max) * 100).toFixed(1)));

		var downHours = Math.floor(down / 60);
		var downMinutes = down % 60;
		var downPercent = Math.max(0, Math.min(100, (100.0 - upPercent).toFixed(1)));

		t.innerHTML = "<em>" + date + "</em>"
			+ "Up: " + upHours + "h " + upMinutes + "min (" + upPercent + "%)"
			+ "<br />" 
			+ "Down: " + downHours + "h " + downMinutes + "min (" + downPercent + "%)";
	}
}

function fetchIncidents()
{
	fetch('/api/incidents', {method: 'GET'})
		.then(response => response.json())
		.then(json =>
		{
			for( var i = 0; i < json.length; i++ )
			{
				const div = document.createElement('div');
				div.className = 'entry';
				
				var date = new Date();
				date.setDate(date.getDate() - i);
				
				const h = document.createElement('h3');
				h.textContent = date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
				div.append(h);
				
				if( json[i].length == 0 )
				{
					const p = document.createElement('p');
					p.className = 'empty';
					p.textContent = "No incidents reported.";
					div.append(p);
				}
				else
				{
					for( const incident of json[i] )
					{
						const p = document.createElement('p');
						
						var s = document.createElement('span');
						s.className = 'title ' + incident.level;
						s.textContent = incident.title;
						p.append(s);
						
						var date = new Date(parseInt(incident.date));
						s = document.createElement('date');
						s.className = 'date';
						s.textContent = date.toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' });
						p.append(s);
						
						s = document.createElement('span');
						s.className = 'summary';
						s.textContent = incident.summary;
						p.append(s);
						
						div.append(p);
					}
				}
				
				document.getElementById('incidents').append(div);
			}
		})
		.catch(error =>
		{
			const p = document.createElement('p');
			p.textContent = 'Could not fetch incidents';
			document.getElementById('incidents').append(p);
			console.log(error);
		});
}