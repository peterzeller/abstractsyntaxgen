package asg.asts;

public class TemplateAsgList {

	public static void writeTo(StringBuilder sb, String commonSupertypeName) {
		sb.append(SOURCE.replace("$ELEMENT$", commonSupertypeName));
	}

	/**
	 * The list of the children of an element, with the bookkeeping of their parents.
	 * <p>
	 * It keeps its elements in an array of its own. It used to wrap an ArrayList, which is a second object for every list
	 * (a tree has a list for the arguments of each call, most of them short), a second load to read an element, and an
	 * array of ten for the first element.
	 */
	private static final String SOURCE = """
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

abstract class AsgList<T> implements List<T>, RandomAccess {
    private static final Object[] NO_ELEMENTS = {};
    /** Up to this many elements, an element is found by looking at all of them, which is faster than an index. */
    private static final int SCAN_LIMIT = 16;

    /** The elements, the first size of them. An empty list shares the empty array until it gets an element. */
    private Object[] elems = NO_ELEMENTS;
    private int size;
    /** The number of structural changes, which the iterators check. */
    private int modCount;
    private IdentityHashMap<T, Integer> identityIndex;

    abstract protected void other_setParentToThis(T t);
    abstract protected void other_clearParent(T t);
    /** Whether this list owns generated children rather than storing references or external values. */
    protected boolean zzOwnsElements() { return false; }

    @SuppressWarnings("unchecked")
    private T at(int index) { return (T) elems[index]; }

    /** Called when the list changed: an element which counts the modifications of what is below it counts this one. */
    protected void zzModified() {}

    private void structureChanged() { modCount++; identityIndex = null; zzModified(); }

    private void buildIdentityIndex() {
        identityIndex = new IdentityHashMap<>(Math.max(4, size));
        for (int i=0, n=size; i<n; i++) identityIndex.put(at(i), i);
    }

    /** The position of the element, compared by identity, or -1. */
    private int identityIndexOf(Object element) {
        int n = size;
        if (n <= SCAN_LIMIT) {
            for (int i=0; i<n; i++) if (elems[i] == element) return i;
            return -1;
        }
        if (identityIndex == null) buildIdentityIndex();
        Integer index = identityIndex.get(element);
        if (index == null || index >= n || elems[index] != element) return -1;
        return index;
    }

    // -------- capacity ----------
    private void grow(int minCapacity) {
        int old = elems.length;
        elems = Arrays.copyOf(elems, Math.max(minCapacity, Math.max(3, old + (old >> 1))));
    }
    /** Room for that many more elements. A list which is filled in one go gets the room it needs and no more. */
    private void reserve(int additional) {
        int needed = size + additional;
        if (needed > elems.length) {
            if (size == 0) elems = new Object[needed]; else grow(needed);
        }
    }
    void ensureCapacity(int capacity) {
        if (capacity > elems.length) elems = Arrays.copyOf(elems, capacity);
    }
    public void trimToSize() {
        if (size < elems.length) elems = size == 0 ? NO_ELEMENTS : Arrays.copyOf(elems, size);
    }

    /** Takes the elements as children, or none of them if one cannot be (it is in a tree already). */
    @SuppressWarnings("unchecked")
    private Object[] prepareAll(Collection<? extends T> elements) {
        int n = elements.size();
        Object[] prepared = new Object[n];
        int done = 0;
        try {
            if (elements instanceof List<?>) {
                List<? extends T> source = (List<? extends T>) elements;
                for (; done < n; done++) { T element = source.get(done); other_setParentToThis(element); prepared[done] = element; }
            } else {
                for (T element : elements) {
                    if (done == n) break;
                    other_setParentToThis(element);
                    prepared[done++] = element;
                }
            }
            return prepared;
        } catch (RuntimeException | Error failure) {
            for (int i=0; i<done; i++) other_clearParent((T) prepared[i]);
            throw failure;
        }
    }
    private Set<?> membershipSet(Collection<?> elements) {
        if (elements instanceof Set<?>) return (Set<?>) elements;
        HashSet<Object> result = new HashSet<>(Math.max(4, elements.size()));
        if (elements instanceof List<?>) {
            List<?> source = (List<?>) elements;
            for (int i=0, n=source.size(); i<n; i++) result.add(source.get(i));
        } else {
            result.addAll(elements);
        }
        return result;
    }

    // -------- core add/remove ----------
    @Override public boolean add(T t) {
        other_setParentToThis(t);
        if (size == elems.length) grow(size + 1);
        elems[size++] = t;
        structureChanged();
        return true;
    }
    public void addFront(T t) { add(0, t); }

    public List<T> removeAll() {
        ArrayList<T> result = new ArrayList<>(Math.max(size, 4));
        for (int i=0, n=size; i<n; i++) {
            T t = at(i);
            other_clearParent(t);
            result.add(t);
        }
        elems = NO_ELEMENTS;
        size = 0;
        structureChanged();
        return result;
    }

    /** Moves all owned children from source to the end of this list. */
    public boolean addAllMoved(AsgList<? extends T> source) { return addAllMoved(size, source); }

    /**
     * Moves all owned children from source into this list at index, preserving their order.
     * Source is empty afterwards. Both lists must own their children and must be distinct.
     * An empty destination takes the source array; otherwise only the destination needs capacity.
     * A failed parent attachment restores the original parents and leaves both lists unchanged.
     */
    public boolean addAllMoved(int index, AsgList<? extends T> source) {
        Objects.requireNonNull(source);
        if (index < 0 || index > size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        if (source == this) throw new IllegalArgumentException("Cannot move a list into itself");
        if (!zzOwnsElements() || !source.zzOwnsElements()) throw new IllegalArgumentException("Only owned children can be moved");
        int count = source.size;
        if (count == 0) return false;
        // Moving a node into a list below that node would create a parent cycle.
        for ($ELEMENT$ ancestor = ($ELEMENT$) this; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getParent() == source) throw new IllegalArgumentException("Cannot move an ancestor into its descendant");
        }
        boolean takeArray = size == 0;
        if (!takeArray) reserve(count); // Allocate before changing parents.
        moveParentsFrom(source, count);
        if (takeArray) {
            elems = source.elems;
        } else {
            System.arraycopy(elems, index, elems, index + count, size - index);
            System.arraycopy(source.elems, 0, elems, index, count);
        }
        size += count;
        source.elems = NO_ELEMENTS;
        source.size = 0;
        source.structureChanged();
        structureChanged();
        return true;
    }

    private <S extends T> void moveParentsFrom(AsgList<S> source, int count) {
        int moved = 0;
        try {
            for (; moved < count; moved++) {
                S element = source.at(moved);
                source.other_clearParent(element);
                try { other_setParentToThis(element); }
                catch (RuntimeException | Error failure) { source.other_setParentToThis(element); throw failure; }
            }
        } catch (RuntimeException | Error failure) {
            for (int i = moved - 1; i >= 0; i--) {
                S element = source.at(i);
                other_clearParent(element);
                source.other_setParentToThis(element);
            }
            throw failure;
        }
    }

    @Override public void add(int index, T elem) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        other_setParentToThis(elem);
        if (size == elems.length) grow(size + 1);
        System.arraycopy(elems, index, elems, index + 1, size - index);
        elems[index] = elem;
        size++;
        structureChanged();
    }

    @Override public boolean addAll(Collection<? extends T> c) {
        return addAll(size, c);
    }

    @Override public boolean addAll(int pos, Collection<? extends T> c) {
        if (pos < 0 || pos > size) throw new IndexOutOfBoundsException("Index: " + pos + ", Size: " + size);
        if (c.isEmpty()) return false;
        Object[] prepared = prepareAll(c);
        int count = prepared.length;
        reserve(count);
        System.arraycopy(elems, pos, elems, pos + count, size - pos);
        System.arraycopy(prepared, 0, elems, pos, count);
        size += count;
        structureChanged();
        return true;
    }

    @Override public void clear() {
        for (int i=0, n=size; i<n; i++) other_clearParent(at(i));
        Arrays.fill(elems, 0, size, null);
        size = 0;
        structureChanged();
    }

    // -------- queries ----------
    @Override public boolean contains(Object o) { return indexOf(o) >= 0; }
    @Override public boolean containsAll(Collection<?> c) {
        if (c instanceof List<?>) {
            List<?> source = (List<?>) c;
            for (int i=0, n=source.size(); i<n; i++) if (!contains(source.get(i))) return false;
            return true;
        }
        for (Object o : c) if (!contains(o)) return false;
        return true;
    }
    @Override public T get(int index) {
        Objects.checkIndex(index, size);
        return at(index);
    }
    @Override public int indexOf(Object o) {
        if (o == null) {
            for (int i=0, n=size; i<n; i++) if (elems[i] == null) return i;
        } else {
            for (int i=0, n=size; i<n; i++) if (o.equals(elems[i])) return i;
        }
        return -1;
    }
    @Override public boolean isEmpty() { return size == 0; }
    @Override public int lastIndexOf(Object o) {
        if (o == null) {
            for (int i=size-1; i>=0; i--) if (elems[i] == null) return i;
        } else {
            for (int i=size-1; i>=0; i--) if (o.equals(elems[i])) return i;
        }
        return -1;
    }
    @Override public void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        for (int i=0, n=size; i<n; i++) action.accept(get(i));
    }
    @Override public Spliterator<T> spliterator() { return new AsgListSpliterator(0, -1, 0); }

    private final class AsgListSpliterator implements Spliterator<T> {
        private int index;
        private int fence;
        private int expectedModCount;
        AsgListSpliterator(int index, int fence, int expectedModCount) {
            this.index = index;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }
        private int fence() {
            if (fence < 0) { expectedModCount = modCount; fence = size; }
            return fence;
        }
        private void check() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
        }
        @Override public Spliterator<T> trySplit() {
            int hi = fence(), lo = index, mid = lo + ((hi - lo) >>> 1);
            check();
            if (lo >= mid) return null;
            index = mid;
            return new AsgListSpliterator(lo, mid, expectedModCount);
        }
        @Override public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            int hi = fence();
            check();
            if (index >= hi) return false;
            action.accept(at(index++));
            check();
            return true;
        }
        @Override public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            int hi = fence();
            check();
            while (index < hi) {
                action.accept(at(index++));
                check();
            }
        }
        @Override public long estimateSize() { return fence() - index; }
        @Override public int characteristics() { return ORDERED | SIZED | SUBSIZED; }
    }

    // -------- iterators (remove() and set() keep the parents right) ----------
    @Override public Iterator<T> iterator() {
        return new AsgListIterator(0);
    }

    @Override public ListIterator<T> listIterator(int index) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException("Index: " + index);
        return new AsgListIterator(index);
    }
    @Override public ListIterator<T> listIterator() { return new AsgListIterator(0); }

    final class AsgListIterator implements ListIterator<T> {
        private int cursor;
        private int lastReturned = -1;
        private int expectedModCount = modCount;
        AsgListIterator(int index) { cursor = index; }
        private void checkForComodification() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
        }
        @Override public boolean hasNext() { return cursor != size; }
        @Override public T next() {
            checkForComodification();
            int i = cursor;
            if (i >= size) throw new NoSuchElementException();
            cursor = i + 1;
            lastReturned = i;
            return at(i);
        }
        @Override public boolean hasPrevious() { return cursor != 0; }
        @Override public T previous() {
            checkForComodification();
            int i = cursor - 1;
            if (i < 0) throw new NoSuchElementException();
            cursor = i;
            lastReturned = i;
            return at(i);
        }
        @Override public int nextIndex() { return cursor; }
        @Override public int previousIndex() { return cursor - 1; }
        @Override public void remove() {
            if (lastReturned < 0) throw new IllegalStateException();
            checkForComodification();
            AsgList.this.remove(lastReturned);
            cursor = lastReturned;
            lastReturned = -1;
            expectedModCount = modCount;
        }
        @Override public void set(T e) {
            if (lastReturned < 0) throw new IllegalStateException();
            checkForComodification();
            other_clearParent(at(lastReturned));
            other_setParentToThis(e);
            elems[lastReturned] = e;
            identityIndex = null;
            zzModified();
        }
        @Override public void add(T e) {
            checkForComodification();
            int i = cursor;
            AsgList.this.add(i, e);
            cursor = i + 1;
            lastReturned = -1;
            expectedModCount = modCount;
        }
    }

    @Override public boolean remove(Object o) {
        int index = indexOf(o);
        if (index < 0) return false;
        remove(index);
        return true;
    }
    @Override public T remove(int index) {
        Objects.checkIndex(index, size);
        T t = at(index);
        int tail = size - index - 1;
        if (tail > 0) System.arraycopy(elems, index + 1, elems, index, tail);
        elems[--size] = null;
        other_clearParent(t);
        structureChanged();
        return t;
    }

    /** Removes the elements which the filter accepts, in one pass. A filter which throws leaves the list as it was. */
    private boolean removeMatching(Predicate<? super T> filter) {
        int n = size;
        BitSet gone = null;
        for (int i=0; i<n; i++) {
            if (filter.test(at(i))) {
                if (gone == null) gone = new BitSet(n);
                gone.set(i);
            }
        }
        if (gone == null) return false;
        int kept = 0;
        for (int i=0; i<n; i++) {
            T t = at(i);
            if (gone.get(i)) other_clearParent(t); else elems[kept++] = t;
        }
        Arrays.fill(elems, kept, n, null);
        size = kept;
        structureChanged();
        return true;
    }

    @Override public boolean removeAll(Collection<?> c) {
        if (c.isEmpty()) return false;
        final Set<?> set = membershipSet(c);
        return removeMatching(t -> set.contains(t));
    }

    @Override public boolean retainAll(Collection<?> c) {
        final Set<?> set = membershipSet(c);
        return removeMatching(t -> !set.contains(t));
    }

    @Override public boolean removeIf(Predicate<? super T> filter) {
        Objects.requireNonNull(filter);
        return removeMatching(filter);
    }

    @Override public T set(int index, T element) {
        Objects.checkIndex(index, size);
        T old = at(index);
        if (old == element) return old;
        other_setParentToThis(element);
        elems[index] = element;
        other_clearParent(old);
        identityIndex = null;
        zzModified();
        return old;
    }
    @Override public void replaceAll(UnaryOperator<T> operator) {
        Objects.requireNonNull(operator);
        for (int i=0, n=size; i<n; i++) set(i, operator.apply(at(i)));
    }
    @SuppressWarnings("unchecked")
    @Override public void sort(Comparator<? super T> comparator) {
        Arrays.sort((T[]) elems, 0, size, comparator);
        structureChanged();
    }

    @Override public int size() { return size; }

    // The part of the list which subList returns keeps the parents right through the list itself
    @Override public List<T> subList(int fromIndex, int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, size);
        return new SubList(fromIndex, toIndex - fromIndex);
    }

    private final class SubList extends AbstractList<T> implements RandomAccess {
        private final int offset;
        private int length;
        private int expectedModCount = AsgList.this.modCount;
        SubList(int offset, int length) { this.offset = offset; this.length = length; }
        private void check() {
            if (AsgList.this.modCount != expectedModCount) throw new ConcurrentModificationException();
        }
        @Override public T get(int index) {
            Objects.checkIndex(index, length);
            check();
            return at(offset + index);
        }
        @Override public int size() { check(); return length; }
        @Override public T set(int index, T element) {
            Objects.checkIndex(index, length);
            check();
            return AsgList.this.set(offset + index, element);
        }
        @Override public void add(int index, T element) {
            if (index < 0 || index > length) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + length);
            check();
            AsgList.this.add(offset + index, element);
            length++;
            expectedModCount = AsgList.this.modCount;
            this.modCount++;
        }
        @Override public T remove(int index) {
            Objects.checkIndex(index, length);
            check();
            T removed = AsgList.this.remove(offset + index);
            length--;
            expectedModCount = AsgList.this.modCount;
            this.modCount++;
            return removed;
        }
    }

    @Override public Object[] toArray() { return Arrays.copyOf(elems, size); }
    @SuppressWarnings("unchecked")
    @Override public <S> S[] toArray(S[] a) {
        if (a.length < size) return (S[]) Arrays.copyOf(elems, size, a.getClass());
        System.arraycopy(elems, 0, a, 0, size);
        if (a.length > size) a[size] = null;
        return a;
    }

    // ---------- tree utilities ----------
    public boolean structuralEquals($ELEMENT$ e) {
        if (e instanceof AsgList) {
            AsgList<?> o = (AsgList<?>) e;
            int n = size; if (o.size != n) return false;
            for (int i=0; i<n; i++) {
                $ELEMENT$ a = ($ELEMENT$) elems[i];
                $ELEMENT$ b = ($ELEMENT$) o.elems[i];
                if (!a.structuralEquals(b)) return false;
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public void forEachElement(Consumer<? super $ELEMENT$> action) {
        Consumer rawAction = (Consumer) action;
        for (int i=0, n=size; i<n; i++) rawAction.accept(elems[i]);
    }

    /** replace first occurrence by identity (==) */
    public boolean replaceExact(Object oldElem, T newElem) {
        int index = identityIndexOf(oldElem);
        if (index < 0) return false;
        T curr = at(index);
        if (curr == newElem) return true;
        other_setParentToThis(newElem);
        elems[index] = newElem;
        if (curr != null) other_clearParent(curr);
        if (identityIndex != null) {
            identityIndex.remove(curr);
            identityIndex.put(newElem, index);
        }
        zzModified();
        return true;
    }

    /**
     * Replaces the first occurrence by identity (==) by the given elements, in order. No element removes it.
     * The new elements must not be in a tree, and none of them may be the element they replace. Nothing
     * changes when that fails. Returns false when the element is not in the list.
     */
    public boolean replaceExactByAll(Object oldElem, Collection<? extends T> newElems) {
        if (newElems.size() == 1) {
            return replaceExact(oldElem, newElems.iterator().next());
        }
        int index = identityIndexOf(oldElem);
        if (index < 0) return false;
        T curr = at(index);
        Object[] prepared = prepareAll(newElems);
        int count = prepared.length;
        if (count > 1) reserve(count - 1);
        System.arraycopy(elems, index + 1, elems, index + count, size - index - 1);
        System.arraycopy(prepared, 0, elems, index, count);
        size += count - 1;
        if (count == 0) elems[size] = null;
        other_clearParent(curr);
        structureChanged();
        return true;
    }

    /**
     * Replaces each element which is a key of the map by the elements of its value, in order, in one pass over
     * the list however many elements are replaced (replacing them one by one is a pass each). An empty value
     * removes the element; the values must not be null. The keys are looked up as the map compares them, so
     * pass an IdentityHashMap to replace by identity. The new elements must not be in a tree, and none of them
     * may be the element they replace. Nothing changes when that fails. Returns the number of elements replaced.
     */
    public int replaceEach(Map<?, ? extends Collection<? extends T>> replacements) {
        if (replacements.isEmpty() || size == 0) return 0;
        ArrayList<T> result = null;
        ArrayList<T> replaced = new ArrayList<>();
        ArrayList<T> prepared = new ArrayList<>();
        try {
            for (int i=0, n=size; i<n; i++) {
                T element = at(i);
                Collection<? extends T> by = replacements.get(element);
                if (by == null) {
                    if (result != null) result.add(element);
                    continue;
                }
                if (result == null) {
                    result = new ArrayList<>(n + by.size());
                    for (int j=0; j<i; j++) result.add(at(j));
                }
                replaced.add(element);
                for (T added : by) { other_setParentToThis(added); prepared.add(added); result.add(added); }
            }
        } catch (RuntimeException | Error failure) {
            for (int i=0, n=prepared.size(); i<n; i++) other_clearParent(prepared.get(i));
            throw failure;
        }
        if (result == null) return 0;
        for (int i=0, n=replaced.size(); i<n; i++) other_clearParent(replaced.get(i));
        elems = result.toArray();
        size = elems.length;
        structureChanged();
        return replaced.size();
    }
}
""";

}
