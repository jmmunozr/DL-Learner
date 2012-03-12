package org.dllearner.algorithm.tbsl.exploration.Sparql;

import edu.stanford.nlp.io.EncodingPrintWriter.out;

public class Hypothesis {
private String variable;
private String uri;
private float rank;
private String name;

/**
 * RESOURCE,PROPERTY,UNSPEC
 */
private String type;

public String getUri() {
	return uri;
}
public void setUri(String uri) {
	this.uri = uri;
}
public String getVariable() {
	return variable;
}
public void setVariable(String variable) {
	this.variable = variable;
}
public float getRank() {
	return rank;
}
public void setRank(float rank) {
	this.rank = rank;
}

public Hypothesis(String variable, String name, String uri, String type, float rank){
	this.setRank(rank);
	this.setVariable(variable);
	this.setUri(uri);
	this.setType(type);
	this.setName(name);
}

public String getType() {
	return type;
}
public void setType(String type) {
	this.type = type;
}

public void printAll(){
	System.out.println("%%%%%%%%%%%");
	System.out.println("Variable: "+variable);
	System.out.println("Name: "+name);
	System.out.println("Uri: " + uri);
	System.out.println("Type: " + type);
	System.out.println("Rank: "+rank);
	System.out.println("%%%%%%%%%%%");
}
public String getName() {
	return name;
}
public void setName(String name) {
	this.name = name;
}

}