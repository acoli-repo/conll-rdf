/*
 * Copyright [2017] [ACoLi Lab, Prof. Dr. Chiarcos, Goethe University Frankfurt]
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acoli.conll.rdf;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelChangedListener;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

public class ChangeListener implements ModelChangedListener {
	
	private boolean change = false;
	private List<String> recentlyAddedStatements = new ArrayList<String>();

	@Override
	public void addedStatement(Statement arg0) {
		this.change = !this.recentlyAddedStatements.contains(arg0.getModel().toString());
		if (!this.change)
			this.recentlyAddedStatements.clear();
		this.recentlyAddedStatements.add(arg0.getModel().toString());
	}

	@Override
	public void addedStatements(Statement[] arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}

	@Override
	public void addedStatements(List<Statement> arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();		
	}

	@Override
	public void addedStatements(StmtIterator arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();			
	}

	@Override
	public void addedStatements(Model arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}

	@Override
	public void notifyEvent(Model arg0, Object arg1) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}

	@Override
	public void removedStatement(Statement arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}

	@Override
	public void removedStatements(Statement[] arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}

	@Override
	public void removedStatements(List<Statement> arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}

	@Override
	public void removedStatements(StmtIterator arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}

	@Override
	public void removedStatements(Model arg0) {
		this.change = true;
		this.recentlyAddedStatements.clear();
	}
	
	public boolean hasChanged() {
		boolean result = this.change;
		this.change = false;
		return result;
	}
	
}