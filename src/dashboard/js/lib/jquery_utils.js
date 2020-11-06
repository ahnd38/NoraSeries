$.fn.exists = function () {
   return this.length !== 0;
}

function selectorEscape(val){
	return val.replace(/[ !"#$%&'()*+,.\/:;<=>?@\[\\\]^`{|}~]/g, '\\$&');
}

function htmlEntities( str, proc ) {
   if ( 'encode' === proc ) {
      str = $('<div/>').text( str ).html();
      return str.replace( '\'', '&apos;' ).replace( '"', '&quot;' );
   } else {
      return $("<div/>").html( str ).text();
   }
}

function setPageTitle(title){
   $('title').text(title);
}
