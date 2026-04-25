package com.sebdeveloper6952;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A 2-3 tree implementation of {@link Map}.
 * <p>
 * Every internal node holds either one key (2-node, two children) or two keys
 * (3-node, three children). All leaves are at the same depth, so search,
 * insert, and delete all run in {@code O(log n)} worst-case time.
 * <p>
 * The algorithms follow the classic formulation: insertion propagates overflow
 * upward by splitting transient 4-nodes; deletion propagates underflow upward
 * via redistribution or fusion with a sibling.
 */
public final class TwoThreeTree<K, V> implements Map<K, V> {

    private final Comparator<? super K> comparator;
    private Node<K, V> root;
    private int size;

    /** Uses the natural ordering of keys; keys must implement {@link Comparable}. */
    public TwoThreeTree() {
        this(null);
    }

    /** Uses the given comparator for key ordering. */
    public TwoThreeTree(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public V get(K key) {
        Entry<K, V> entry = findEntry(key);
        return entry == null ? null : entry.value;
    }

    @Override
    public boolean containsKey(K key) {
        return findEntry(key) != null;
    }

    @Override
    public V put(K key, V value) {
        Objects.requireNonNull(key, "key");
        if (root == null) {
            root = Node.leafOf(new Entry<>(key, value));
            size = 1;
            return null;
        }

        PutContext<V> ctx = new PutContext<>();
        SplitResult<K, V> split = insert(root, key, value, ctx);

        if (split != null) {
            Node<K, V> newRoot = new Node<>();
            newRoot.entries[0] = split.promoted;
            newRoot.numKeys = 1;
            newRoot.children[0] = root;
            newRoot.children[1] = split.newRight;
            root = newRoot;
        }

        if (!ctx.replaced) size++;
        return ctx.previousValue;
    }

    @Override
    public V remove(K key) {
        Objects.requireNonNull(key, "key");
        if (root == null) return null;

        RemoveResult<V> result = delete(root, key);
        if (root.numKeys == 0) {
            root = root.children[0];
        }
        if (result.removed) size--;
        return result.value;
    }

    /** @return all keys in this map, in ascending order. */
    public List<K> keysInOrder() {
        List<K> keys = new ArrayList<>(size);
        collect(root, keys);
        return keys;
    }

    // -----------------------------------------------------------------------
    // Search
    // -----------------------------------------------------------------------

    private Entry<K, V> findEntry(K key) {
        Objects.requireNonNull(key, "key");
        Node<K, V> curr = root;
        while (curr != null) {
            int cmp0 = compare(key, curr.entries[0].key);
            if (cmp0 == 0) return curr.entries[0];
            if (cmp0 < 0) {
                curr = curr.children[0];
            } else if (curr.is2Node()) {
                curr = curr.children[1];
            } else {
                int cmp1 = compare(key, curr.entries[1].key);
                if (cmp1 == 0) return curr.entries[1];
                curr = (cmp1 < 0) ? curr.children[1] : curr.children[2];
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Insertion
    // -----------------------------------------------------------------------

    private SplitResult<K, V> insert(Node<K, V> node, K key, V value, PutContext<V> ctx) {
        int cmp0 = compare(key, node.entries[0].key);
        if (cmp0 == 0) {
            ctx.previousValue = node.entries[0].value;
            ctx.replaced = true;
            node.entries[0].value = value;
            return null;
        }
        if (node.is3Node()) {
            int cmp1 = compare(key, node.entries[1].key);
            if (cmp1 == 0) {
                ctx.previousValue = node.entries[1].value;
                ctx.replaced = true;
                node.entries[1].value = value;
                return null;
            }
        }

        if (node.isLeaf()) {
            return insertIntoLeaf(node, new Entry<>(key, value));
        }

        int childIdx = childIndexFor(node, key);
        SplitResult<K, V> childSplit = insert(node.children[childIdx], key, value, ctx);
        if (childSplit == null) return null;

        return absorbOrSplit(node, childIdx, childSplit.promoted, childSplit.newRight);
    }

    private SplitResult<K, V> insertIntoLeaf(Node<K, V> leaf, Entry<K, V> entry) {
        if (leaf.is2Node()) {
            if (compare(entry.key, leaf.entries[0].key) < 0) {
                leaf.entries[1] = leaf.entries[0];
                leaf.entries[0] = entry;
            } else {
                leaf.entries[1] = entry;
            }
            leaf.numKeys = 2;
            return null;
        }

        Entry<K, V> a, b, c;
        if (compare(entry.key, leaf.entries[0].key) < 0) {
            a = entry; b = leaf.entries[0]; c = leaf.entries[1];
        } else if (compare(entry.key, leaf.entries[1].key) < 0) {
            a = leaf.entries[0]; b = entry; c = leaf.entries[1];
        } else {
            a = leaf.entries[0]; b = leaf.entries[1]; c = entry;
        }

        leaf.entries[0] = a;
        leaf.entries[1] = null;
        leaf.numKeys = 1;
        return new SplitResult<>(b, Node.leafOf(c));
    }

    private SplitResult<K, V> absorbOrSplit(Node<K, V> node, int childIdx, Entry<K, V> promoted, Node<K, V> newRight) {
        if (node.is2Node()) {
            if (childIdx == 0) {
                node.entries[1] = node.entries[0];
                node.entries[0] = promoted;
                node.children[2] = node.children[1];
                node.children[1] = newRight;
            } else {
                node.entries[1] = promoted;
                node.children[2] = newRight;
            }
            node.numKeys = 2;
            return null;
        }

        Entry<K, V> a, b, c;
        Node<K, V> c0, c1, c2, c3;
        if (childIdx == 0) {
            a = promoted; b = node.entries[0]; c = node.entries[1];
            c0 = node.children[0]; c1 = newRight; c2 = node.children[1]; c3 = node.children[2];
        } else if (childIdx == 1) {
            a = node.entries[0]; b = promoted; c = node.entries[1];
            c0 = node.children[0]; c1 = node.children[1]; c2 = newRight; c3 = node.children[2];
        } else {
            a = node.entries[0]; b = node.entries[1]; c = promoted;
            c0 = node.children[0]; c1 = node.children[1]; c2 = node.children[2]; c3 = newRight;
        }

        node.entries[0] = a;
        node.entries[1] = null;
        node.numKeys = 1;
        node.children[0] = c0;
        node.children[1] = c1;
        node.children[2] = null;

        Node<K, V> right = new Node<>();
        right.entries[0] = c;
        right.numKeys = 1;
        right.children[0] = c2;
        right.children[1] = c3;
        return new SplitResult<>(b, right);
    }

    // -----------------------------------------------------------------------
    // Deletion
    // -----------------------------------------------------------------------

    private RemoveResult<V> delete(Node<K, V> node, K key) {
        int idx = indexOfKey(node, key);
        if (idx >= 0) {
            RemoveResult<V> result = new RemoveResult<>();
            result.value = node.entries[idx].value;
            result.removed = true;

            if (node.isLeaf()) {
                if (idx == 0 && node.numKeys == 2) node.entries[0] = node.entries[1];
                node.entries[1] = null;
                node.numKeys--;
                result.underflow = node.numKeys == 0;
                return result;
            }

            Node<K, V> succ = node.children[idx + 1];
            while (!succ.isLeaf()) succ = succ.children[0];
            Entry<K, V> replacement = succ.entries[0];
            node.entries[idx] = replacement;
            RemoveResult<V> sub = delete(node.children[idx + 1], replacement.key);
            if (sub.underflow) fixUnderflow(node, idx + 1);
            return result;
        }

        if (node.isLeaf()) return new RemoveResult<>();

        int childIdx = childIndexFor(node, key);
        RemoveResult<V> sub = delete(node.children[childIdx], key);
        if (sub.underflow) fixUnderflow(node, childIdx);
        return sub;
    }

    private void fixUnderflow(Node<K, V> parent, int emptyIdx) {
        if (emptyIdx > 0 && parent.children[emptyIdx - 1].is3Node()) {
            redistributeFromLeft(parent, emptyIdx);
        } else if (emptyIdx < parent.numKeys && parent.children[emptyIdx + 1].is3Node()) {
            redistributeFromRight(parent, emptyIdx);
        } else {
            fuse(parent, (emptyIdx > 0) ? emptyIdx - 1 : emptyIdx);
        }
    }

    private void redistributeFromLeft(Node<K, V> parent, int emptyIdx) {
        Node<K, V> empty = parent.children[emptyIdx];
        Node<K, V> left = parent.children[emptyIdx - 1];
        empty.entries[0] = parent.entries[emptyIdx - 1];
        empty.children[1] = empty.children[0];
        empty.children[0] = left.children[2];
        empty.numKeys = 1;
        parent.entries[emptyIdx - 1] = left.entries[1];
        left.entries[1] = null;
        left.children[2] = null;
        left.numKeys = 1;
    }

    private void redistributeFromRight(Node<K, V> parent, int emptyIdx) {
        Node<K, V> empty = parent.children[emptyIdx];
        Node<K, V> right = parent.children[emptyIdx + 1];
        empty.entries[0] = parent.entries[emptyIdx];
        empty.children[1] = right.children[0];
        empty.numKeys = 1;
        parent.entries[emptyIdx] = right.entries[0];
        right.entries[0] = right.entries[1];
        right.entries[1] = null;
        right.children[0] = right.children[1];
        right.children[1] = right.children[2];
        right.children[2] = null;
        right.numKeys = 1;
    }

    private void fuse(Node<K, V> parent, int leftIdx) {
        Node<K, V> left = parent.children[leftIdx];
        Node<K, V> right = parent.children[leftIdx + 1];
        if (left.numKeys == 0) {
            left.entries[0] = parent.entries[leftIdx];
            left.entries[1] = right.entries[0];
            left.children[1] = right.children[0];
            left.children[2] = right.children[1];
        } else {
            left.entries[1] = parent.entries[leftIdx];
            left.children[2] = right.children[0];
        }
        left.numKeys = 2;
        if (leftIdx == 0 && parent.numKeys == 2) {
            parent.entries[0] = parent.entries[1];
            parent.children[1] = parent.children[2];
        }
        parent.entries[1] = null;
        parent.children[2] = null;
        parent.numKeys--;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private int compare(K a, K b) {
        return (comparator != null) ? comparator.compare(a, b) : ((Comparable<? super K>) a).compareTo(b);
    }

    private int indexOfKey(Node<K, V> node, K key) {
        if (compare(key, node.entries[0].key) == 0) return 0;
        if (node.is3Node() && compare(key, node.entries[1].key) == 0) return 1;
        return -1;
    }

    private int childIndexFor(Node<K, V> node, K key) {
        int cmp0 = compare(key, node.entries[0].key);
        if (cmp0 < 0) return 0;
        if (node.is2Node()) return 1;
        return (compare(key, node.entries[1].key) < 0) ? 1 : 2;
    }

    private void collect(Node<K, V> node, List<K> keys) {
        if (node == null) return;
        collect(node.children[0], keys);
        keys.add(node.entries[0].key);
        collect(node.children[1], keys);
        if (node.is3Node()) {
            keys.add(node.entries[1].key);
            collect(node.children[2], keys);
        }
    }

    // -----------------------------------------------------------------------
    // Internal Representation
    // -----------------------------------------------------------------------

    private static final class Entry<K, V> {
        final K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class Node<K, V> {
        final Entry<K, V>[] entries;
        final Node<K, V>[] children;
        int numKeys;

        @SuppressWarnings("unchecked")
        private Node() {
            this.entries = (Entry<K, V>[]) new Entry[2];
            this.children = (Node<K, V>[]) new Node[3];
        }

        static <K, V> Node<K, V> leafOf(Entry<K, V> entry) {
            Node<K, V> n = new Node<>();
            n.entries[0] = entry;
            n.numKeys = 1;
            return n;
        }

        boolean isLeaf()   { return children[0] == null; }
        boolean is2Node()  { return numKeys == 1; }
        boolean is3Node()  { return numKeys == 2; }
    }

    private static final class SplitResult<K, V> {
        final Entry<K, V> promoted;
        final Node<K, V> newRight;

        SplitResult(Entry<K, V> promoted, Node<K, V> newRight) {
            this.promoted = promoted;
            this.newRight = newRight;
        }
    }

    private static final class PutContext<V> {
        V previousValue;
        boolean replaced;
    }

    private static final class RemoveResult<V> {
        V value;
        boolean removed;
        boolean underflow;
    }
}