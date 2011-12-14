package aurelienribon.tweenengine;

import aurelienribon.tweenengine.TimelineCallback.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * A Timeline can be used to create complex animations made of sequences and
 * parallel sets of Tweens.
 * <br/><br/>
 *
 * The following example will create an animation sequence composed of 5 parts:
 * <br/><br/>
 *
 * 1. First, Opacity and Scale are reset to 0. <br/>
 * 2. Then, Opacity and Scale are tweened to 1. <br/>
 * 3. Then, the animation is paused for 1s. <br/>
 * 4. Then, Position is tweened to x=100. <br/>
 * 5. Then, Rotation is tweened to 360°.
 * <br/><br/>
 *
 * This animation will be repeated 5 times, with a 500ms delay between each
 * iteration.
 * 
 * <pre>
 * Timeline.createSequence()
 *     .beginParallel()
 *         .push(Tween.set(myObject, OPACITY).target(0))
 *         .push(Tween.set(myObject, SCALE).target(0, 0))
 *     .end()
 *     .beginParallel()
 *          .push(Tween.to(myObject, OPACITY, 500).target(1).ease(Quad.INOUT))
 *          .push(Tween.to(myObject, SCALE, 500).target(1, 1).ease(Quad.INOUT))
 *     .end()
 *     .pushPause(1000)
 *     .push(Tween.to(myObject, POSITION_X, 500).target(100).ease(Quad.INOUT))
 *     .push(Tween.to(myObject, ROTATION, 500).target(360).ease(Quad.INOUT))
 *     .repeat(5, 500)
 *     .start(myManager);
 * </pre>
 *
 * @see Tween
 * @see TweenManager
 * @author Aurelien Ribon | http://www.aurelienribon.com/
 */
public class Timeline extends TimelineObject {
	// -------------------------------------------------------------------------
	// Static -- pool
	// -------------------------------------------------------------------------

	private static final Pool.Callback<Timeline> poolCallback = new Pool.Callback<Timeline>() {
		@Override public void onPool(Timeline obj) {obj.reset();}
		@Override public void onUnpool(Timeline obj) {obj.isPooled = Tween.isPoolingEnabled();}
	};

	static final Pool<Timeline> pool = new Pool<Timeline>(15, poolCallback) {
		@Override protected Timeline create() {Timeline tl = new Timeline(); tl.reset(); return tl;}
	};

	/**
	 * Used for debug purpose. Gets the current number of objects that are
	 * waiting in the pool.
	 * @return The current size of the pool.
	 */
	public static int getPoolSize() {
		return pool.size();
	}

	/**
	 * Increases the pool capacity directly. Capacity defaults to 20.
	 * @param minCapacity The minimum capacity of the pool.
	 */
	public static void ensurePoolCapacity(int minCapacity) {
		pool.ensureCapacity(minCapacity);
	}

	// -------------------------------------------------------------------------
	// Static -- factories
	// -------------------------------------------------------------------------

	public static Timeline createSequence() {
		Timeline root = pool.get();
		root.type = Modes.SEQUENCE;
		return root;
	}

	public static Timeline createParallel() {
		Timeline root = pool.get();
		root.type = Modes.PARALLEL;
		return root;
	}

	// -------------------------------------------------------------------------
	// Attributes
	// -------------------------------------------------------------------------

	private enum Modes {SEQUENCE, PARALLEL}

	// Main
	private final List<TimelineObject> children = new ArrayList<TimelineObject>(10);
	private Timeline parent;
	private Modes type;

	// General
	private boolean isPooled;
	private boolean isYoyo;
	private boolean isComputeIteration;
	private int iteration;
	private int repeatCnt;

	// Timings
	private int delayMillis;
	private int durationMillis;
	private int repeatDelayMillis;
	private int currentMillis;
	private boolean isStarted;
	private boolean isInitialized;
	private boolean isFinished;

	// -------------------------------------------------------------------------
	// Ctor
	// -------------------------------------------------------------------------

	private void reset() {
		children.clear();
		parent = null;

		isPooled = Tween.isPoolingEnabled();
		isYoyo = isComputeIteration = false;
		iteration = repeatCnt = 0;

		delayMillis = durationMillis = repeatDelayMillis = currentMillis = 0;
		isStarted = isInitialized = isFinished = false;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	public Timeline beginSequence() {
		Timeline child = pool.get();
		child.parent = this;
		child.type = Modes.SEQUENCE;
		return child;
	}

	public Timeline beginParallel() {
		Timeline child = pool.get();
		child.parent = this;
		child.type = Modes.PARALLEL;
		return child;
	}

	public Timeline end() {
		if (parent == null) throw new RuntimeException("Nothing to end...");
		return parent;
	}

	public Timeline push(Tween tween) {
		children.add(tween);
		return this;
	}

	public Timeline push(Timeline timeline) {
		timeline.parent = this;
		children.add(timeline);
		return this;
	}

	public Timeline pushPause(int millis) {
		children.add(Tween.mark().delay(millis));
		return this;
	}

	public Timeline repeat(int count, int delayMillis) {
		this.repeatCnt = count;
		this.repeatDelayMillis = delayMillis;
		this.isYoyo = false;
		return this;
	}

	public Timeline repeatYoyo(int count, int delayMillis) {
		this.repeatCnt = count;
		this.repeatDelayMillis = delayMillis;
		this.isYoyo = true;
		return this;
	}

	public Timeline start() {
		if (parent != null) throw new RuntimeException("You forgot to call a few 'end()' statements...");
		sequence(this);
		return this;
	}

	public Timeline start(TweenManager manager) {
		manager.add(this);
		return this;
	}

	@Override
	public void kill() {
		isFinished = true;
	}

	/**
	 * If you want to manually manage your timelines (without using a
	 * TweenManager), and you enabled object pooling, then you need to call
	 * this method on your timelines once they are finished (see <i>isFinished()
	 * </i> method).
	 */
	@Override
	public void free() {
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			obj.free();
		}
		if (isPooled) pool.free(this);
	}

	// -------------------------------------------------------------------------
	// Getters
	// -------------------------------------------------------------------------

	public int getDuration() {
		return durationMillis;
	}

	public int getRepeatCount() {
		return repeatCnt;
	}

	public int getRepeatDelay() {
		return repeatDelayMillis;
	}

	/**
	 * Returns true if the timeline is finished (i.e. if it has reached
	 * its end or has been killed). If you don't use a TweenManager, and enabled
	 * object pooling, then don't forget to call <i>Timeline.free()</i> on your
	 * timelines once <i>isFinished()</i> returns true.
	 */
	@Override
	public boolean isFinished() {
		return isFinished;
	}

	/**
	 * Returns the complete duration of a timeline, including its delay and its
	 * repetitions. The formula is as follows:
	 * <br/><br/>
	 *
	 * fullDuration = delay + duration + (repeatDelay + duration) * repeatCnt
	 */
	@Override
	public int getFullDuration() {
		return delayMillis + durationMillis + (repeatDelayMillis + durationMillis) * repeatCnt;
	}

	// -------------------------------------------------------------------------
	// Update engine
	// -------------------------------------------------------------------------

	/**
	 * Updates the timeline state. <b>You may want to use a TweenManager to
	 * update timelines for you.</b> Slow motion, fast motion and backwards play
	 * can be easily achieved by tweaking the deltaMillis given as parameter.
	 * @param deltaMillis A delta time, in milliseconds, between now and the
	 * last call.
	 */
	@Override
	public void update(int deltaMillis) {
		if (!isStarted) return;

		int lastIteration = iteration;
		currentMillis += deltaMillis;

		initialize();

		if (isInitialized) {
			testRelaunch();
			updateIteration();
			testInnerTransition(lastIteration);
			testLimitTransition(lastIteration);
			testCompletion();
			if (isComputeIteration) compute();
		}
	}

	private void initialize() {
		if (!isInitialized && currentMillis >= delayMillis) {
			isInitialized = true;
			isComputeIteration = true;
			currentMillis -= delayMillis;
			callCallbacks(Types.BEGIN);
			callCallbacks(Types.START);
		}
	}

	private void testRelaunch() {
		if (repeatCnt >= 0 && iteration > repeatCnt*2 && currentMillis <= 0) {
			assert iteration == repeatCnt*2 + 1;
			isComputeIteration = true;
			currentMillis -= durationMillis;
			iteration = repeatCnt*2;

		} else if (repeatCnt >= 0 && iteration < 0 && currentMillis >= 0) {
			assert iteration == -1;
			isComputeIteration = true;
			iteration = 0;
		}
	}

	private void updateIteration() {
		while (isValid(iteration)) {
			if (!isComputeIteration && currentMillis <= 0) {
				isComputeIteration = true;
				currentMillis += durationMillis;
				iteration -= 1;
				callCallbacks(Types.BACK_START);

			} else if (!isComputeIteration && currentMillis >= repeatDelayMillis) {
				isComputeIteration = true;
				currentMillis -= repeatDelayMillis;
				iteration += 1;
				callCallbacks(Types.START);

			} else if (isComputeIteration && currentMillis < 0) {
				isComputeIteration = false;
				currentMillis += isValid(iteration-1) ? repeatDelayMillis : 0;
				iteration -= 1;
				callCallbacks(Types.BACK_END);

			} else if (isComputeIteration && currentMillis > durationMillis) {
				isComputeIteration = false;
				currentMillis -= durationMillis;
				iteration += 1;
				callCallbacks(Types.END);

			} else break;
		}
	}

	private void testInnerTransition(int lastIteration) {
		if (isComputeIteration) return;
		if (iteration > lastIteration) forceEndValues(iteration-1);
		else if (iteration < lastIteration) forceStartValues(iteration+1);
	}

	private void testLimitTransition(int lastIteration) {
		if (repeatCnt < 0 || iteration == lastIteration) return;
		if (iteration > repeatCnt*2) callCallbacks(Types.COMPLETE);
		else if (iteration < 0) callCallbacks(Types.BACK_COMPLETE);
	}

	private void testCompletion() {
		isFinished = (repeatCnt >= 0 && iteration > repeatCnt*2) || (repeatCnt >= 0 && iteration < 0);
	}

	private void compute() {
		assert currentMillis >= 0;
		assert currentMillis <= durationMillis;
		assert isInitialized;
		assert !isFinished;
		assert isComputeIteration;
		assert isValid(iteration);

		int millis = isIterationYoyo(iteration) ? durationMillis - currentMillis : currentMillis;
		for (int i=0; i<children.size(); i++) {
			TimelineObject obj = children.get(i);
			obj.setCurrentMillis(millis);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void sequence(Timeline tl) {
		tl.durationMillis = 0;

		for (int i=0; i<tl.children.size(); i++) {
			TimelineObject obj = tl.children.get(i);

			if (obj instanceof Tween) {
				Tween child = (Tween) obj;
				if (tl.type == Modes.SEQUENCE) child.delay(tl.durationMillis);
				tl.durationMillis = Math.max(tl.durationMillis, child.getFullDuration());

			} else if (obj instanceof Timeline) {
				Timeline child = (Timeline) obj;
				if (tl.type == Modes.SEQUENCE) child.delayMillis = tl.durationMillis;
				sequence(child);
				tl.durationMillis = Math.max(tl.durationMillis, child.getFullDuration());
			}
		}
	}
	
	private void forceStartValues(int iteration) {
		int millis = isIterationYoyo(iteration) ? durationMillis : 0;
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			obj.setCurrentMillis(millis);
		}
	}

	private void forceEndValues(int iteration) {
		int millis = isIterationYoyo(iteration) ? 0 : durationMillis;
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			obj.setCurrentMillis(millis);
		}
	}

	private boolean isValid(int iteration) {
		return (iteration >= 0 && iteration <= repeatCnt*2) || repeatCnt < 0;
	}

	private boolean isIterationYoyo(int iteration) {
		return isYoyo && Math.abs(iteration%4) == 2;
	}

	private void callCallbacks(TimelineCallback.Types type) {
		List<TimelineCallback> callbacks = null;

		/*switch (type) {
			case BEGIN: callbacks = startCallbacks; break;
			case START: callbacks = startCallbacks; break;
			case END: callbacks = endCallbacks; break;
			case COMPLETE: callbacks = endCallbacks; break;
			case BACK_START: callbacks = backStartCallbacks; break;
			case BACK_END: callbacks = backEndCallbacks; break;
			case BACK_COMPLETE: callbacks = backEndCallbacks; break;
		}*/

		if (callbacks != null && !callbacks.isEmpty())
			for (int i=0, n=callbacks.size(); i<n; i++)
				callbacks.get(i).timelineEventOccured(type, this);
	}

	// -------------------------------------------------------------------------
	// TimelineObject impl.
	// -------------------------------------------------------------------------

	@Override
	protected void setCurrentMillis(int millis) {
		update(millis - currentMillis);
	}

	@Override
	protected int getChildrenCount() {
		int cnt = 0;
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			cnt += 1 + obj.getChildrenCount();
		}
		return cnt;
	}

	@Override
	protected void killTarget(Object target) {
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			obj.killTarget(target);
		}
	}

	@Override
	protected void killTarget(Object target, int tweenType) {
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			obj.killTarget(target, tweenType);
		}
	}

	@Override
	protected boolean containsTarget(Object target) {
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			if (obj.containsTarget(target)) return true;
		}
		return false;
	}

	@Override
	protected boolean containsTarget(Object target, int tweenType) {
		for (int i=0, n=children.size(); i<n; i++) {
			TimelineObject obj = children.get(i);
			if (obj.containsTarget(target, tweenType)) return true;
		}
		return false;
	}
}
