package com.github.haha1028.unreliable.util;

import java.util.ArrayList;

public class UnreliableArrayList<E> extends ArrayList<E> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -778572826903142326L;
	UnreliablePolicy policy = UnreliablePolicy.RELIABLE_POLICY();

	public UnreliableArrayList(int initialCapacity, UnreliablePolicy policy) {
		super(initialCapacity);
		this.policy = policy;
	}

}
