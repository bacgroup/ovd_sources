<?php
/**
 * Copyright (C) 2010 Ulteo SAS
 * http://www.ulteo.com
 * Author Jeremy DESVAGES <jeremy@ulteo.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 **/
require_once(dirname(__FILE__).'/../includes/core-minimal.inc.php');
require_once(dirname(__FILE__).'/../includes/webservices.inc.php');

function parse_icon_XML($xml_) {
	if (! $xml_ || strlen($xml_) == 0)
		return false;

	$dom = new DomDocument('1.0', 'utf-8');

	$buf = @$dom->loadXML($xml_);
	if (! $buf)
		return false;

	if (! $dom->hasChildNodes())
		return false;

	$ret = array(
		'server'		=>	$_SERVER['REMOTE_ADDR'],
		'application'	=>	array()
	);

	$application_node = $dom->getElementsByTagName('application')->item(0);
	if (is_null($application_node))
		return false;

	if (! $application_node->hasAttribute('id'))
		return false;

	$ret['application']['id'] = $application_node->getAttribute('id');

	return $ret;
}

$ret = parse_icon_XML(@file_get_contents('php://input'));
if (! $ret) {
	Logger::error('main', '(webservices/application/icon) Server does not send a valid XML (error_code: 1)');
	webservices_return_error(1, 'Server does not send a valid XML');
}

$server = webservices_load_server($_SERVER['REMOTE_ADDR']);
if (! $server) {
	Logger::error('main', '(webservices/application/icon) Server does not exist (error_code: 2)');
	webservices_return_error(2, 'Server does not exist');
}

if (! $server->isAuthorized()) {
	Logger::error('main', '(webservices/application/icon) Server is not authorized (error_code: 3)');
	webservices_return_error(3, 'Server is not authorized');
}

$applicationDB = ApplicationDB::getInstance();

$app = $applicationDB->import($ret['application']['id']);
if (! is_object($app)) {
	Logger::error('main', '(webservices/application/icon) No such application "'.$ret['application']['id'].'" (error_code: 4)');
	webservices_return_error(4, 'No such application "'.$ret['application']['id'].'"');
}

$path = $app->getIconPath();
if (! file_exists($path)) {
	Logger::error('main', '(webservices/application/icon) No icon available for application "'.$ret['application']['id'].'" (error_code: 5)');
	webservices_return_error(5, 'No icon available for application "'.$ret['application']['id'].'"');
}

header('Content-Type: image/png');
echo @file_get_contents($path, LOCK_EX);
