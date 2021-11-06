; Test case: making sure all type of comments are parsed correctly

	ld a,1	; this should be optimized
	ld a,2	// this should be optimized too
/*
	ld a,3	this line should be ignored
*/
{
	ld a,4  this line should be ignored too
}
	ld a, /* in-line comment */ 5
	ld (var), {another in-line comment} a	
end:
	jp end /*

And one final case to be ignored:

	ld a,6
	*/

var:
	db 0
