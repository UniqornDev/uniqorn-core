function acquire(event)
{
	event.preventDefault();
	document.getElementById('consent').style.display = 'block';
	var s = document.getElementById('signature');
	if( !s )
	{
		s = document.createElement('input');
		s.type = 'hidden';
		s.id = 'signature';
		document.body.append(s);
	}
	document.body.querySelectorAll('input[type="checkbox"]').forEach(c => c.checked = false);
	document.querySelector('#consent button.publish').disabled = true;
	
	fetch('/api/captcha?op=deploy', {method: 'GET'})
		.then(response => response.json())
		.then(json =>
		{
			if( json.error )
				throw new Error(json.error.message);
			s.value = json.token;
		})
		.catch(error =>
		{
			document.getElementById('consent').style.display = 'none';
			let r = document.getElementById('result');
			r.style.display = 'block';
			r = r.firstChild;
			while(r.firstChild) r.firstChild.remove();
			const h2 = document.createElement('H2');
			h2.textContent = "Warning";
			r.append(h2);
			const p = document.createElement('P');
			p.className = 'error';
			p.innerHTML = "It seems the system is a bit overloaded at the moment so publishing is not available.<br />We are very sorry for the poor user experience this situation creates.<br />Please give us another chance and try again later...";
			r.append(p);
			const b = document.createElement('BUTTON');
			b.className = 'close';
			b.textContent = "Close";
			b.addEventListener('click', function(e) { e.preventDefault(); document.getElementById('result').style.display = 'none'; });
			r.append(b);
		});
}

function publish()
{
	document.getElementById('consent').style.display = 'none';
	let r = document.getElementById('result');
	r.style.display = 'block';
	r = r.firstChild;
	while(r.firstChild) r.firstChild.remove();
	const h2 = document.createElement('H2');
	h2.textContent = "Publishing...";
	r.append(h2);
	
	const code = new FormData();
	code.append("code", document.getElementById('source').value);
	code.append("captcha", document.getElementById('signature').value);
	
	fetch('/api/public/deploy', {method: 'POST', body: code})
		.then(response => response.json())
		.then(json =>
		{
			if( json.error )
			{
				throw new Error(json.error.message);
			}
			else if( json.url )
			{
				while(r.firstChild) r.firstChild.remove();
				const h2 = document.createElement('H2');
				h2.textContent = "Success";
				r.append(h2);
				const p = document.createElement('P');
				p.className = 'success';
				p.innerHTML = "Your endpoint is ready. To avoid name clash, we created a unique URL:<br /><br />";
				r.append(p);
				const a = document.createElement('A');
				a.href = json.url;
				a.target = '_blank';
				a.textContent = json.url;
				p.append(a);
				const b = document.createElement('BUTTON');
				b.className = 'done';
				b.textContent = "Done";
				b.addEventListener('click', function(e) { e.preventDefault(); document.getElementById('result').style.display = 'none'; });
				r.append(b);
			}
		})
		.catch(error =>
		{
			while(r.firstChild) r.firstChild.remove();
			const h2 = document.createElement('H2');
			h2.textContent = "Error";
			r.append(h2);
			const p = document.createElement('P');
			p.className = 'error';
			p.textContent = error.message;
			r.append(p);
			const b = document.createElement('BUTTON');
			b.className = 'close';
			b.textContent = "Close";
			b.addEventListener('click', function(e) { e.preventDefault(); document.getElementById('result').style.display = 'none'; });
			r.append(b);
		});
}