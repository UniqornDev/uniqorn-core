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
	
	fetch('/api/public/deploy', {method: 'POST', body: code})
		.then(response => response.json())
		.then(json =>
		{
			if( json.error )
			{
				throw new Error(json.error);
			}
			else if( json.url )
			{
				while(r.firstChild) r.firstChild.remove();
				const h2 = document.createElement('H2');
				h2.textContent = "Success";
				r.append(h2);
				const p = document.createElement('P');
				p.className = 'success';
				p.textContent = "Your endpoint is ready. We created a unique URL: ";
				r.append(p);
				const a = document.createElement('A');
				a.href = json.url;
				a.textContent = json.url;
				p.append(a);
			}
		})
		.catch(error =>
		{
			debugger;
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