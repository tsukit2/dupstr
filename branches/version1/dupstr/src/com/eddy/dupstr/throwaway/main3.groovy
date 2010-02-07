package com.eddy.dupstr.throwaway


import java.text.*

import com.eddy.suffixtree.handler.StringElementHandler 

import com.eddy.suffixtree.SuffixTreeUtils;
import com.eddy.suffixtree.naiveimpl.NaiveSuffixTree;

com.eddy.dupstr.throwaway.data = new File('resources/testdata.txt').text + '$'
//data << '$$$'
def tree = new NaiveSuffixTree<Character,String>(
      new StringElementHandler(data, true))





//println "=========="
def reps = SuffixTreeUtils.longRepeatedElementPaths(tree, 30)
reps = reps.collect { it.elementPath }
//reps = reps.findAll { path -> path.inject(0) { i,it -> it.matches(/\w+/) ? i+1 : i } >= 30 }
reps = reps.collect { it.trim() }.sort { a,b->b.size()-a.size() }
reps.eachWithIndex { str, index ->
   println "${index + 1}: \"${str}\""
}





def getWordData(filename) {
   def data = []
   def str = new File(filename).text
   def boundary = BreakIterator.getWordInstance(new Locale('en', 'US'))
   boundary.setText(str)
   int start = boundary.first();
   int end = boundary.next();
   def SEP = System.properties['line.separator']
   while(end != BreakIterator.DONE) {
      def s = str.substring(start,end)//.trim()
      //if (s) data << s
      //if(s) {
      //if(s && !s.matches(/ +/)) {
         data << s
      //}
      start = end
      end = boundary.next()
   }
   return data
}

