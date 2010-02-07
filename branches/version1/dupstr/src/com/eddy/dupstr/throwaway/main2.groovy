package com.eddy.dupstr.throwaway


import java.text.*

import com.eddy.suffixtree.handler.StringListElementHandler;

import com.eddy.suffixtree.SuffixTreeUtils;
import com.eddy.suffixtree.naiveimpl.NaiveSuffixTree;

com.eddy.dupstr.throwaway.data = getWordData('resources/testdata.txt')
data << '$$$'
def tree = new NaiveSuffixTree<String,List<String>>(
      new StringListElementHandler(data, false))





//println "=========="
def reps = SuffixTreeUtils.longRepeatedElementPaths(tree, 5)
reps = reps.collect { it.elementPath }
reps = reps.findAll { path -> path.inject(0) { i,it -> it.matches(/\w+/) ? i+1 : i } >= 5 }
reps = reps.collect { it.join('').trim() }.sort { a,b->b.size()-a.size() }
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

