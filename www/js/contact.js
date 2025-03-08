var contactTriggered = false;
document.addEventListener("DOMContentLoaded", () =>
{
	const f = document.body.querySelector("form");
	if( !f ) return;

	f.addEventListener("focusin", () =>
	{
		if (contactTriggered) return;
		contactTriggered = true;
		setTimeout(() =>
		{
			fetch('/api/captcha?op=contact', {method: 'GET'})
				.then(res => res.json())
				.then(data =>
				{
					var i = document.createElement('input');
					i.type = "hidden";
					i.name = "captcha";
					i.value = data.token;
					f.append(i);
				})
				.catch(error =>
				{
					let r = document.getElementById('result');
					r.style.display = 'block';
					r = r.firstChild;
					while(r.firstChild) r.firstChild.remove();
					const h2 = document.createElement('H2');
					h2.textContent = "Warning";
					r.append(h2);
					const p = document.createElement('P');
					p.className = 'error';
					p.innerHTML = "Caution, it seems the system is a bit overloaded at the moment so the contact form is not available.<br />Try sending us an email at <em>contact@aeonics.be</em> instead.";
					r.append(p);
					const b = document.createElement('BUTTON');
					b.className = 'close';
					b.textContent = "Close";
					b.addEventListener('click', function(e) { e.preventDefault(); document.getElementById('result').style.display = 'none'; });
					r.append(b);
				});
		}, 3000);
	});
});

function send(event)
{
	event.preventDefault();
	const f = document.body.querySelector("form");
	
	var r = document.getElementById('result');
	r.style.display = 'block';
	r = r.firstChild;
	while(r.firstChild) r.firstChild.remove();
	const h2 = document.createElement('H2');
	h2.textContent = "Sending...";
	r.append(h2);
		
	fetch('/api/contact', {method: 'POST', body: new FormData(f)})
		.then(response => response.json())
		.then(json =>
		{
			if( json.error )
				throw new Error(json.error.message);
			
			while(r.firstChild) r.firstChild.remove();
			const h2 = document.createElement('H2');
			h2.textContent = "Sent!";
			r.append(h2);
			const p = document.createElement('P');
			p.className = 'success';
			p.innerHTML = "Your message has been delivered.";
			r.append(p);
			const b = document.createElement('BUTTON');
			b.className = 'close';
			b.textContent = "Close";
			b.addEventListener('click', function(e) { e.preventDefault(); document.getElementById('result').style.display = 'none'; });
			r.append(b);
			
			contactTriggered = false;
			f.querySelector('input[name="captcha"]').remove();
			f.dispatchEvent(new Event("focusin"));
		})
		.catch(error =>
		{
			let r = document.getElementById('result');
			r.style.display = 'block';
			r = r.firstChild;
			while(r.firstChild) r.firstChild.remove();
			const h2 = document.createElement('H2');
			h2.textContent = "Error";
			r.append(h2);
			const p = document.createElement('P');
			p.className = 'error';
			p.innerHTML = "Wow, the contact form did not work !<br />How are you supposed to contact us if the contact form is broken, huh?<br />Please try with a regular email at <em>contact@aeonics.be</em>, it will probably work better...<br />What a shame!";
			r.append(p);
			const b = document.createElement('BUTTON');
			b.className = 'close';
			b.textContent = "Close";
			b.addEventListener('click', function(e) { e.preventDefault(); document.getElementById('result').style.display = 'none'; });
			r.append(b);
		});
}