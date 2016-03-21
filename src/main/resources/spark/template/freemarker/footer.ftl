  <script>ds_params = {'navbar': 'li_home'};</script>

</div><!-- /.container -->

<!-- Mustache template for toc entries -->
<!-- See https://github.com/janl/mustache.js -->
<script id="toc_item_template" type="x-tmpl-mustache">
<li class="toc_item">
	<h4 class="{{envelope_status_class}}">Envelope: {{envelope_status}}</h4>
	{{#recipients}}
		<p>{{type}}: {{user_name}}<br/>
			<span class="{{status_class}}">Status: {{status}}</span>
		</p>
	{{/recipients}}
</li>
</script>

<!-- Mustache template for displaying xml file -->
<!-- XML in Ace editor, see http://stackoverflow.com/a/16147926/64904 -->
<script id="xml_file_template" type="x-tmpl-mustache">
	<h3>XML Notification Content</h3>
	<h5 class="{{envelope_status_class}}">Envelope status: {{envelope_status}}</h5>
	<p class="margintop">Recipients</p>
	<ul>
	{{#recipients}}
		<li>{{type}}: {{user_name}} <span class="{{status_class}}">Status: {{status}}</span></li>
	{{/recipients}}
	</ul>
	<h5><a href='{{xml_url}}' target='_blank'>Download the XML file</a></h5>
	{{#documents.length}}
		<p>Documents<ul>
			{{#documents}}
				<li>Document ID {{document_ID}}: {{name}} <a href='{{url}}' target='_blank'>Download</a></li>
			{{/documents}}
		</ul></p>
	{{/documents.length}}
</script>

<!-- Bootstrap core JavaScript -->
<script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js"></script>
<!-- Latest compiled and minified JavaScript -->
<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js" integrity="sha384-0mSbJDEHialfmuBBQP6A4Qrprq5OVfW37PRR3j5ELqxss1yVqOtnepnHVP9aJ7xS" crossorigin="anonymous"></script><!-- IE10 viewport hack for Surface/desktop Windows 8 bug -->
<script src="assets_master/ie10-viewport-bug-workaround.js"></script>
<script src="bower_components/mustache.js/mustache.min.js"></script>
<script src="https://cdn.jsdelivr.net/g/ace@1.2.2(noconflict/ace.js+noconflict/mode-xml.js+noconflict/theme-chrome.js)"></script>

<script src="010.webhook.js"></script> <!-- nb. different assets directory -->
</body>
</html>