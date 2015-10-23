package com.yahoo.semsearch.fastlinking;

import it.unimi.dsi.fastutil.io.BinIO;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.yahoo.semsearch.fastlinking.entityranker.EntityRelevanceJudgment;
import com.yahoo.semsearch.fastlinking.hash.CountAndRecordStats;
import com.yahoo.semsearch.fastlinking.hash.QuasiSuccinctEntityHash;
import com.yahoo.semsearch.fastlinking.view.EntityContext;
import com.yahoo.semsearch.fastlinking.w2v.CentroidEntityContext;
import com.yahoo.semsearch.fastlinking.w2v.LREntityContext;

/**
 * Entity linker that can use a context class to score candidates.
 * Use like 
 * java -Xmx10G com.yahoo.semsearch.fastlinking.EntityContextFastEntityLinker -h data/newhash -e data/ENTITIES.PHRASE.model -u data/PHRASE.model -m scratch/type_data/entity2types.txt
 * @author roi
 *
 */
public class EntityContextFastEntityLinker extends FastEntityLinker {

    public EntityContextFastEntityLinker( QuasiSuccinctEntityHash hash, EntityContext queryContext ) {
	super( hash, hash.stats, queryContext );
    }

    public EntityContextFastEntityLinker( QuasiSuccinctEntityHash hash, CountAndRecordStats stats, EntityContext queryContext ) {
	super( hash, stats, queryContext );
    }

    
    public static void main( String args[] ) throws Exception {

	SimpleJSAP jsap = new SimpleJSAP( EntityContextFastEntityLinker.class.getName(), "Interactive mode for entity linking", new Parameter[] {
		new FlaggedOption( "hash", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'h', "hash", "quasi succint hash" ),
		new FlaggedOption( "unigrams", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'u', "unigrams", "Unigram word vectors file" ),
		new FlaggedOption( "types", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 't', "types", "Type mapping file (for display)" ),
		new FlaggedOption( "labels", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'l', "labels", "File containing query2entity labels" ),
		new FlaggedOption( "id2type", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'i', "id2type", "File with the id2type mapping" ),
		new Switch( "centroid", 'c', "centroid", "Use centroid-based distances and not LR" ),
		new FlaggedOption( "map", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'm', "map", "Entity 2 type mapping " ),
		new FlaggedOption( "entities", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'e', "entities", "Entities word vectors file" ), } );

	JSAPResult jsapResult = jsap.parse( args );
	if ( jsap.messagePrinted() ) return;

	double threshold = 0.001;
	QuasiSuccinctEntityHash hash = (QuasiSuccinctEntityHash) BinIO.loadObject( jsapResult.getString( "hash" ) );	
	EntityContext queryContext;
	if ( !jsapResult.getBoolean( "centroid" ) ) {
	    queryContext = new LREntityContext( jsapResult.getString( "unigrams" ), jsapResult.getString( "entities" ), hash );
	} else {
	    queryContext = new CentroidEntityContext( jsapResult.getString( "unigrams" ), jsapResult.getString( "entities" ), hash );
	}
	HashMap<String, ArrayList<EntityRelevanceJudgment>> labels = null;
	if ( jsapResult.getString( "labels" ) != null ) {
	    labels = readTrainingDataALP( jsapResult.getString( "labels" ) );
	}

	String map = jsapResult.getString( "map" );

	HashMap<String, String> entities2Type = null;

	if ( map != null ) entities2Type = readEntity2IdFile( map );

	String types = jsapResult.getString( "types" );
	HashMap<Short, String> typeMapping; //only for display purposes
	if ( types != null ) {
	    typeMapping = readTypeMapping( types );
	} else {
	    typeMapping = new HashMap<Short, String>();
	    for ( short i = 0; i < Short.MAX_VALUE; i++ )
		typeMapping.put( i, new Short( i ).toString() );
	}
	EntityContextFastEntityLinker linker = new EntityContextFastEntityLinker( hash, hash.stats, queryContext );
	//linker.context = queryContext;

	final BufferedReader br = new BufferedReader( new InputStreamReader( System.in ) );
	String q;
	for ( ;; ) {
	   // System.out.print( ">" );
	    q = br.readLine();
	    if ( q == null ) {
		System.err.println();
		break; // CTRL-D
	    }
	    if ( q.length() == 0 ) continue;
	    long time = -System.nanoTime();
	    try {
		List<EntityResult> results = linker.getResults( q, threshold );
		//List<EntityResult> results = linker.getResultsGreedy( q, 5 );
		//int rank = 0;
		for ( EntityResult er : results ) {		    
		    if ( entities2Type != null ) {				
			String name = er.text.toString().trim();
			String newType = entities2Type.get( name );
			if ( newType == null ) newType = "NF";
		//	System.out.println( q + "\t span: \033[1m [" + er.text + "] \033[0m eId: " + er.id + " ( t= " + newType + ")" + "  score: " + er.score + " ( "
		//		+ er.s.span + " ) " );
			
			System.out.println( newType + "\t" + q + "\t" + StringUtils.remove( q, er.s.span.toString() ) + " \t " + er.text );
			break;
			/* } else {
			   System.out.print( "[" + er.text + "(" + String.format("%.2f",er.score) +")] ");
			   System.out.println( "span: \033[1m [" + er.text + "] \033[0m eId: " + er.id + " ( t= " + typeMapping.get( hash.getEntity( er.id ).type )
				+ "  score: " + er.score + " ( " + er.s.span + " ) " );
			}
			rank++;
			*/			
		    } else {
			if ( labels == null ) {
			    System.out.println( q + "\t" + er.text + "\t" + er.score );
			} else {
			    ArrayList<EntityRelevanceJudgment> jds = labels.get( q );
			    String label = "NF";
			    if ( jds != null ) {
				EntityRelevanceJudgment relevanceOfEntity = relevanceOfEntity( er.text, jds );
				label = relevanceOfEntity.label;
			    }
			    System.out.println( q + "\t" + er.text + "\t" + label + "\t" + er.score );
			    break;
			}
		    }
		    System.out.println();
		}
		time += System.nanoTime();
		System.out.println( "Time to rank and print the candidates:" + time / 1000000. + " ms" );
	    } catch ( Exception e ) {
		e.printStackTrace();
	    }
	}
    }

    /**
     * Reads a type mapping file (from short integer to string). Useful for display
     * @param types
     * @return
     * @throws IOException
     */
    public static HashMap<Short, String> readTypeMapping( String types ) throws IOException {
	HashMap<Short, String> typeMapping = new HashMap<Short, String>();
	final BufferedReader lines = new BufferedReader( new FileReader( types ) );
	String line;
	while ( ( line = lines.readLine() ) != null ) {
	    String[] parts = line.split( "\t" );
	    try {
		typeMapping.put( new Short( parts[ 1 ] ), parts[ 0 ] );		
	    } catch ( NumberFormatException e ) {
		System.out.println( "Wrong line: " + line );
		e.printStackTrace();
	    }
	}
	lines.close();
	return typeMapping;
    }

    /**
     * Reads an entity to identifier mapping file.
     * @param entities
     * @return
     * @throws IOException
     */
    public static HashMap<String, String> readEntity2IdFile( String entities ) throws IOException	 {
	HashMap<String, String> entity2Id = new HashMap<String, String>();
	final BufferedReader lines = new BufferedReader( new FileReader( entities ) );
	String line;
	while ( ( line = lines.readLine() ) != null ) {
	    String[] parts = line.split( "\t" );
	    try {
		String name = parts[ 2 ].replaceAll( " ", "_" );
		entity2Id.put( name , parts[ 3 ] );
	    } catch ( NumberFormatException e ) {
		System.out.println( "Wrong line: " + line );
	    }
	}
	lines.close();
	return entity2Id;
    }

 
}