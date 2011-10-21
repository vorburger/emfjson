/*******************************************************************************
 * Copyright (c) 2011 Guillaume Hillairet.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Guillaume Hillairet - initial API and implementation
 *******************************************************************************/
package org.eclipselabs.emfjson.internal;

import static org.eclipselabs.emfjson.internal.EJsUtil.getElementName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;

/**
 * 
 * @author guillaume
 *
 */
public class EJsMapper {

	protected final ObjectMapper mapper;
	protected JsonNode rootNode;
	
	public EJsMapper() {
		this.mapper = new ObjectMapper();
	}

	public ObjectMapper getMapper() {
		return mapper;
	}

	public JsonNode genJson(Resource resource, Map<?, ?> options) {
		final JsonNode rootNode;

		if (resource.getContents().size() == 1) {

			EObject rootObject = resource.getContents().get(0);
			rootNode = writeEObject(rootObject);

		} else {

			final Collection<JsonNode> nodes = new ArrayList<JsonNode>();
			rootNode = mapper.createArrayNode();

			for (EObject obj: resource.getContents()) {
				JsonNode node = writeEObject(obj);
				if (node != null) {
					nodes.add(node);
				}
			}

			((ArrayNode)rootNode).addAll(nodes);
		}

		return rootNode;
	}

	public JsonNode genJson(Resource resource) {
		return genJson(resource, Collections.emptyMap());
	}

	protected JsonNode writeEObject(EObject object) {

		ObjectNode node = mapper.createObjectNode();

		writeEObjectAttributes(object, node);
		writeEObjectReferences(object, node);

		return node;
	}

	protected void writeEObjectAttributes(EObject object, ObjectNode node) {

		for (EAttribute attribute: object.eClass().getEAllAttributes()) {

			if (object.eIsSet(attribute) && !attribute.isDerived() && !attribute.isTransient() && !attribute.isUnsettable()) {

				if (FeatureMapUtil.isFeatureMap(attribute)) {

					FeatureMap.Internal featureMap = (FeatureMap.Internal) object.eGet(attribute);
					Iterator<FeatureMap.Entry> iterator = featureMap.basicIterator();

					while (iterator.hasNext()) {

						FeatureMap.Entry entry = iterator.next();
						EStructuralFeature feature = entry.getEStructuralFeature();
						
						if (feature instanceof EAttribute) {
							setJsonValue(node, entry.getValue(), (EAttribute) feature);
						}
						
					}

				} else if (attribute.isMany()) {

					EList<?> rawValues = (EList<?>) object.eGet(attribute);

					if (!rawValues.isEmpty()) {
						ArrayNode arrayNode = mapper.createArrayNode();
						node.put(getElementName(attribute), arrayNode);

						for (Object val: rawValues) {
							setJsonValue(arrayNode, val, attribute);
						}
					}

				} else {

					final Object value = object.eGet(attribute);
					setJsonValue(node, value, attribute);

				}
			}
		}
	}

	private void setJsonValue(ObjectNode node, Object value, EAttribute attribute) {
		final EDataType dataType = attribute.getEAttributeType();

		if (value != null) {
			if (dataType.getName().contains("Int")) {
				node.put(getElementName(attribute), (Integer)value);	
			} else if (dataType.getName().contains("Boolean")) {
				node.put(getElementName(attribute), (Boolean)value);
			} else if (dataType.getName().contains("Double")) {
				node.put(getElementName(attribute), (Double)value);
			} else {
				node.put(getElementName(attribute), value.toString());
			}

		}
	}

	private void setJsonValue(ArrayNode node, Object value, EAttribute attribute) {
		final EDataType dataType = attribute.getEAttributeType();

		if (value != null) {
			if (dataType.getName().contains("Int")) {
				node.add((Integer)value);	
			} else if (dataType.getName().contains("Boolean")) {
				node.add((Boolean)value);
			} else if (dataType.getName().contains("Double")) {
				node.add((Double)value);
			} else {
				node.add(value.toString());
			}

		}
	}

	// if reference is non containment, objects are referenced thanks to their ids.
	protected void writeEObjectReferences(EObject object, ObjectNode node) {

		for (EReference reference: object.eClass().getEAllReferences()) {
			
			if (!reference.isTransient() && object.eIsSet(reference)) {

				if (reference.isContainment()) {

					writeEObjectContainments(object, reference, node);

				} else {

					if (reference.isMany()) {

						@SuppressWarnings("unchecked")
						EList<EObject> values = (EList<EObject>) object.eGet(reference);

						if (!values.isEmpty()) {

							final ArrayNode arrayNode = mapper.createArrayNode();
							node.put(getElementName(reference), arrayNode);

							for (EObject obj: values) {
								ObjectNode nodeRef = mapper.createObjectNode();
								nodeRef.put("$ref", EcoreUtil.getURI(obj).fragment());
								arrayNode.add(nodeRef);
							}
						}

					} else {

						Object value = object.eGet(reference);
						if (value != null) {
							node.put(reference.getName(), EcoreUtil.getID((EObject) value));
						}

					}
				}
			}
		}
	}

	// if reference is containment, then objects are put in an ArrayNode
	protected void writeEObjectContainments(EObject object, EReference reference, ObjectNode node) {

		if (reference.isMany()) {

			@SuppressWarnings("unchecked")
			EList<EObject> values = (EList<EObject>) object.eGet(reference);

			if (!values.isEmpty()) {
				final ArrayNode arrayNode = mapper.createArrayNode();
				node.put(getElementName(reference), arrayNode);

				for (EObject obj: values) {
					ObjectNode subNode = arrayNode.addObject();
					writeEObjectAttributes(obj, subNode);
					writeEObjectReferences(obj, subNode);
				}
			}
		} else {

			final Object value = object.eGet(reference);

			if (value != null) {
				final ObjectNode subNode = node.objectNode();

				node.put(getElementName(reference), subNode);
				writeEObjectAttributes((EObject) value, subNode);
				writeEObjectReferences((EObject) value, subNode);
			}
		}

	}

}