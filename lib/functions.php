<?php
$server_root_path = $_SERVER['DOCUMENT_ROOT'];

function put_html_head($title) {
	echo('<head>
	<title>' . $title . '</title>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
	<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
	<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.2.1/jquery.min.js"></script>
	<script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
	<style>
	/* Move down content because of fixed navbar that is 3.5rem tall */
	body {
		padding-top: 4.0rem;
	}
	</style>
</head>');
}

function put_nav() {
	echo('    <nav class="navbar navbar-default navbar-fixed-top" role="navigation">
		<div class="container">
			<div class="navbar-header">
				<a class="navbar-brand" href="/">wiz</a>
				<button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#navbar" aria-expanded="false" aria-controls="navbar">
					<span class="sr-only">Toggle navigation</span>
					<span class="icon-bar"></span>
					<span class="icon-bar"></span>
					<span class="icon-bar"></span>
			   </button>
			</div>

			<div class="collapse navbar-collapse" id="navbar">
				<ul class="nav navbar-nav">
					<!--li class="active">
						<a href="#">Home</a>
					</li-->
					<li>
						<a href="' . $server_root_path . '/noname.html">시간표</a>
					</li>
					<li class="nav-item dropdown">
						<a class="nav-link dropdown-toggle" href="javascript:;" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">Programming</a>
						<ul class="dropdown-menu">
							<li>
								<a href="' . $server_root_path . '/Programming/Android">Android</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/C">C</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/Go">Go</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/Java">Java</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/JavaScript">JavaScript</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/Node.js">Node.js</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/PHP">PHP</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/Python">Python</a>
							</li>
							<li>
								<a href="' . $server_root_path . '/Programming/VisualBasic">VB.NET</a>
							</li>
							<li class="divider"></li>
							<li>
								<a href="' . $server_root_path . '/Programming/Koreatech">Koreatech</a>
							</li>
						</ul>
					</li>
				</ul>
				<!--ul class="nav navbar-nav navbar-right"-->
				<!--form class="form-inline my-2 my-lg-0">
					<input class="form-control mr-sm-2" type="text" placeholder="Search" aria-label="Search">
					<button class="btn btn-outline-success my-2 my-sm-0" type="submit">Search</button>
				</form-->
			</div>
		</div>
	</nav>');
}

function put_default_footer() {
	echo('    <footer class="container">
    <p>
        The favicon used in this site made by <a href="https://www.flaticon.com/authors/smashicons" title="Smashicons">Smashicons</a> from <a href="http://www.flaticon.com">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a>
    </p>
    <p>
        &copy; Donggi Kim 2017
        <br> Contact: hi.donggi@gmail.com
    </p>
	</footer>');
}
?>
