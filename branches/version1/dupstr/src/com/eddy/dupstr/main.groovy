package com.eddy.dupstr

import java.text.*

import groovy.text.SimpleTemplateEngine;

import com.eddy.suffixtree.handler.StringListElementHandler;

import com.eddy.suffixtree.SuffixTreeUtils;
import com.eddy.suffixtree.naiveimpl.NaiveSuffixTree;

// parse the command line, terminate immediately if things are not set correctly
def cli = new CliBuilder(usage: 'java -jar dupstr.jar [options] <inputfile> <outoutfile>')  
cli.i(longOpt: 'ignoreCase', 'true/false whether to ignore case when analyzing document. default = true', required: false, args: 1)  
cli.m(longOpt: 'minimumWords' , 'minimum words to consider. default = 5', required: false, args: 1)
cli.t(longOpt: 'treatSpaceAsWord' , 'true/false whether to treat space as word. default = false', required: false, args: 1)
cli.s(longOpt: 'ignoreSpaces' , 'true/false whether to ignore spaces. default = true', required: false, args: 1)
cli.c(longOpt: 'collapseSpaces' , 'true/false whether to consider multiple consecutive spaces as one. default = true', required: false, args: 1)
def opts = cli.parse(args)
if(!opts || opts.arguments().size() != 2) {
	cli.usage()
   System.exit(1)
}



// extract options for the program
def filename = opts.arguments()[0]
def outputFilename = opts.arguments()[1]
def options = [ignoreCase: opts.i ? opts.i == 'true' : true,
               minimumWords: opts.m ? opts.m as int : 5,
               treatSpaceAsWord: opts.t ? opts.t == 'true' : false,
               ignoreSpaces: opts.s ? opts.s == 'true' : true,
               collapseSpaces: opts.c ? opts.c == 'true' : true,
               locale: new Locale('en', 'US')]

// extract the words from the file. Append extra token to make sure the output tree is valid
// assume everything is English first
def text = new File(filename).text
def words = getWordList(text, options)
words << '$$$@@$$$'

// then get the list of repeated strings and its offsets
def duplist = getRepeatedStringList(words, options).sort { -it.inject(0) { a,b -> a+b.size() } }
def dupstrs = duplist.collect { it.join(options.ignoreSpaces ? ' ' : '') }
def offsets = getRepeatedStringOffsets(duplist, text, options)
text = escapeTextAndUpdateOffsetsForJavaScript(offsets, text)

// now let's generate output from template
def bindings = [filename:filename, originalText:text, offsets:offsets, dupstrs:dupstrs]
def template = new SimpleTemplateEngine().createTemplate(
      this.class.classLoader.getResourceAsStream('com/eddy/dupstr/output-template.html').text).make(bindings)
new File(outputFilename).text = template.toString()

//
//println text
//
//println '==========================='
//
//offsets.each { matches ->
//   matches.each {
//      println text[it[0]..it[1]]
//   }
//}

// escape original text and update the offsets 
def escapeTextAndUpdateOffsetsForJavaScript(offsets, text) {
	// prepare to escape
	def escapedText = new StringBuilder()
	def updateFunc = { x -> x } // begin with identify function
	
	// let's do it by walking through the original text
	def accoffset = 0
	text.eachWithIndex { ch, index ->
		// set the current token and reset offset
		def token = ch
		def curoffset = 0 
		
		// then determine if we need to escape
	   switch(ch) {
		case '<':  token = '&gt;'; curoffset = 3; break;
		case '>':  token = '&lt;'; curoffset = 3; break;
		case '&':  token = '&amp;'; curoffset = 4; break;
		case '"':  token = '\\"'; /*curoffset = 1;*/ break; // no offset as it's still single char in javascript
		case '\n': token = '<br/>'; curoffset = 4; break; 
		case '\r': token = ''; curoffset = -1; break;
		}
		
		// finally add the token into the escaped text and create new update func if there is offset
		escapedText << token
		if(curoffset) {
			// recreate the update function. Here we need to freeze all the value the closure will refer to
			// this is the bad part of groovy. It should have frozen the value. But I think doing that will
			// also create bad things too. Well, groovy nor java is a pure language anyway.
			def offsettedIndex = index + accoffset
			def constoffset = curoffset // freeze the current value so it won't change withint the closure
			def curFunc = updateFunc // freeze
			updateFunc = { x -> def v = curFunc(x); v > offsettedIndex ? v + constoffset : v }
			
			
			// update the accumurate offset
			accoffset += curoffset
		}
	}
	// finally going through the offsets and update it
	offsets.size().times { y ->
		def matches = offsets[y]
	   matches.size().times { x ->
	      def range = matches[x]
	      range[0] = updateFunc(range[0])
	      range[1] = updateFunc(range[1])
		}
	}
	
	// and return the escaped text
	return escapedText
}

// find the offset in the actual text
def getRepeatedStringOffsets(duplist, text, options) {
	// walk through the duplicate list to find the offset
	def offsets = []
	duplist.each { list ->
		// first make sure we escape all special characters
		def matches = []
		def escapedList = list.collect { it.replaceAll('([\\\\.$\\[\\]])', '\\\\$1') }
		
		// the if we care for space but collapse it, turn all single space to a regex
		// that matches space of any length
		if(!options.ignoreSpaces && options.collapseSpaces) {
		   escapedList = escapedList.collect { it == ' ' ? /\s+/ : it }
		}
		
		// then get the matcher against the proper regex. Note that if the we ignore space, we join
		// all the words with regex that "could" match space. Otherwise, we join with nothing. This will
		// work with above if with collapseSpaces flag
      def matcher = text =~ 
         "${options.ignoreCase ? '(?i)' : ''}${escapedList.join(options.ignoreSpaces ? /\s*/ : '')}"
	
      // now extract the match and save it off
      while(matcher.find()) {
		   //matches << [start:matcher.start(), end:matcher.end()]
		   matches << [matcher.start(), matcher.end()-1]
		}
      offsets << matches
	}
	return offsets
}

// extract the list of repeated string in the words list
def getRepeatedStringList(words, options) {
	// using suffix tree to do the job
	def tree = new NaiveSuffixTree<String,List<String>>(new StringListElementHandler(words, options.ignoreCase))
	def reps = SuffixTreeUtils.longRepeatedElementPaths(tree, options.minimumWords)

	// extract the list of the string from the node
	reps = reps.collect { it.elementPath }
	if(!options.ignoreSpaces && !options.treatSpaceAsWord) {
		// if spaces are considered and not treated as word, filter out further by counting only word that
		// meet the minimuwords
	   reps = reps.findAll { path -> path.inject(0) { i,it -> it.matches(/\w+/) ? i+1 : i } >= options.minimumWords }
	}
	
	// finally return the result
	return reps
}




// utility method to extract word list from a given text
def getWordList(str, options) {
	// prepare to break up the text into words
	def data = []
   def boundary = BreakIterator.getWordInstance(options.locale)
   boundary.setText(str)
   int start = boundary.first();
   int end = boundary.next();

   // going through the word boundary
   while(end != BreakIterator.DONE) {
      // extract the portion being looked at
      def s = str.substring(start,end)

      // determine how to treat space
	   if(options.ignoreSpaces) {
	      s = s.trim()
	   } else if(options.collapseSpaces && s.matches(/\s+/)) {
	      s = ' '
	   }
	
      // if anything, add it into the list
      if (s) data << s
	
      // go find the next one
      start = end
      end = boundary.next()
   }

   // finally return it
   return data
}

