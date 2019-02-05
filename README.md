# thjson
Tagged Human JSON

Sooner or later, all developers try to store some sort of structured data to configure their applications in some way.
Perhaps it's simple configuration files pointing to databases, perhaps it's data files for game entities, perhaps it's
i18n translation files.

There are a million data formats that you might consider using. Probably the five most popular formats are:

INI files
XML files
YAML files
JSON files
HJSON files

Each has advantages and disadvantages for various use cases. But what if you wanted a format that comfortably handled ALL
use cases?

INI files can't handle any kind of nested structure. The only concept is blocks of key=value pairs where each value must
fit on a single line. It allows comments.

XML files are huge and verbose, and the syntax is unnecessarily pernickety for human beings to write. It is well-specified
but bloated with features that can tie you in knots. It does cope with arbitrarily complex data well, though this can be
its undoing. You can comment it but often in a restricted manner. But seriously, it's a faff and everybody hates typing it.

YAML is a hideous format designed by devils. It contains a kitchen sink, manufactured in Hell. It too is pernickety with
syntax requiring actually properly formatted whitespace, which is a sure sign of Satan's work. We shall not speak of it
again.

JSON files are very simple to parse for a computer. Unfortunately the syntax is also unnecessarily pernickety for human
beings to type and hurts the eyes somewhat with all the unnecessary quotes and commas, and the only data structures it can
directly deal with are primitive values, arrays, and maps. What's worse is it can't have comments in it, which renders it
pretty useless for humans.

HJSON is getting close to perfection. You can read all about HJSON at https://hjson.org and even try it out in the 
browser. HJSON removes all the unnecessary syntactical cruft from JSON, making mostly everything optional; and it adds a
few new features such as comments in every style imaginable and multiline strings. HJSON is easily converted into JSON for
feeding to other APIs. Unfortunately HJSON has one major flaw, which is that it cannot handle the concept of "classes".
We can have null, numbers, booleans, strings, arrays, and maps, all nested arbitrarily... but we cannot declare a map to
be a structured type, or "class". This is a big pain in the arse if you are dealing with modern OOP languages, because
they deal a whole lot in classes.

Enter THJSON.

THJSON looks almost exactly like HJSON - it is in fact a superset of HJSON, which itself is a superset of JSON. The extra
bit is the addition of a class name in round brackets before a map (maps are objects that are enclosed in {} parentheses):

    left_hand: (sword) {
        damage: 3
        weight: 1kg
    }
    
Essentially any value that is parsed as a string that then starts to define a map with the opening curly brace, becomes
instead a class, of a type name which is the string so far. No whitespace is allowed in the type name unless it is quoted.

We can do the same for arrays - define a class type for the array. We don't actually check that the elements themselves
conform to the type - indeed we don't do anything with the class type name other than pass it to the stream listener:

    inventory: (item) [sword, axe, shoes, tea, "no tea"]
    
In the git repository there's an example listener that converts the stream of tokens into a Google Json object. Classes
are converted into JSON objects by simply creating a property called "class":

    "left_hand":{
        "class":"sword",
        "damage":3,
        "weight":"1kg"
    }

Lists create an object of class "array" with a property "elements":

    "inventory":{
        "class":"array",
        "elements":
            [
                "sword",
                "axe",
                "shoes",
                "tea",
                "no tea"
            ]
        }

That's not the "definitive" way to do it but it's what the example listener does.

Finally I've added a way to handle binary data markup. You can now encode binary data as Base64, tagged in back-apostrophes or whatever they're called on a single line:

	base641:`YW55IGNhcm5hbCBwbGVhcw`
	base642:`YW55IGNhcm5hbCBwbGVhc3U`
	base643:`YW55IGNhcm5hbCBwbGVhc3Vy`
	base644:`YW55IGNhcm5hbCBwbGVhcw==`
	base645:`YW55IGNhcm5hbCBwbGVhc3U=`
	base646:`YW55IGNhcm5hbCBwbGVhc3Vy`

Or if you've got a lot of binary data, you can use multiline - but be aware there are no escapes in multiline binary data. Although you can add comments in it, if you're basically insane:

	multilineBase64test:
	  <<<
	  abcdefghijklmnopqrstuvwxyz
	  ABCDEFGHIJKLMNOPQRSTUVWXYZ
	  0123456789+/
	  >>>

THJSON allows things called directives in it, which are outside of the normal data definition and can be used to trigger things during parsing. Directives must be at the root level in the document hierarchy, begin with a #, and are a single string keyword, followed by any tokens up to a newline. Some examples which might tickle your fancy:

	#define sausages	100
	#include some/resource.thjson
	#fielddef thingy { type: string, mandatory: true }

Yes, that's right, you can implement a C style preprocessor in your token listener! (I've done it, it's quite complex to do right, but it works great).

Finally, it is valid THJSON syntax to include anonymous maps, objects, and arrays, inside another object or map. Or even at the root. So this is fine:

	inventory: (backpack) 
	  {
 	    (sword) { damage: 1 }
 	    (axe) { damage: 2 }
 	    (potion} { health: 3 }
	    {
		// An empty Map			
	    }
	  }

The listener will return anonymous objects in the order that they are encountered.
