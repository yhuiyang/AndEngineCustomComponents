package com.yhsoftlab.andengine;

import java.util.ArrayList;

import org.andengine.audio.sound.Sound;
import org.andengine.entity.Entity;
import org.andengine.entity.IEntity;
import org.andengine.entity.modifier.IEntityModifier.IEntityModifierListener;
import org.andengine.entity.modifier.MoveYModifier;
import org.andengine.entity.modifier.RotationModifier;
import org.andengine.entity.scene.ITouchArea;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.sprite.Sprite;
import org.andengine.entity.sprite.TiledSprite;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.opengl.texture.region.ITiledTextureRegion;
import org.andengine.opengl.vbo.VertexBufferObjectManager;
import org.andengine.util.modifier.IModifier;

import android.util.Log;
import android.util.SparseArray;

public class ExpandableButtonGroup extends Entity {

	// ===========================================================
	// Constants
	// ===========================================================
	private final String TAG = this.getClass().getSimpleName();
	private final int CAPACITY_DEFAULT = 4;
	// private final int ZINDEX_SUB_BUTTON = 1;
	private final int ZINDEX_SUB_BUTTON_BG = 2;
	private final int ZINDEX_SUB_BUTTON_FG = 3;
	// private final int ZINDEX_MAIN_BUTTON = 4;
	private final int ZINDEX_MAIN_BUTTON_BG = 5;
	private final int ZINDEX_MAIN_BUTTON_FG = 6;
	private final float MAIN_BTN_ROTATION_DURATION = 0.3f;
	private final float MAIN_BTN_ROTATION_DEGREE = 180.0f;
	private final float SUB_BTN_MOVE_DURATION = 0.1f;

	// ===========================================================
	// Fields
	// ===========================================================
	private SparseArray<SubButton> mSubButtons;
	private ArrayList<ITouchArea> mPendingTouchAreas;
	private ITextureRegion mMainBtnBgRegion;
	private Sprite mMainBtnBgSprite;
	private Sprite mMainBtnFgSprite;
	private boolean mExpanded = false;
	private boolean mMainBtnRotating = false;
	private final IEntityModifierListener mMainBtnRotationListener;
	private final RotationModifier mMainBtnRotationCW;
	private final RotationModifier mMainBtnRotationCCW;
	private IOnSubButtonClickListener mOnSubButtonClickListener = null;
	private final MoveYModifier[] mSubBtnMoveOut = new MoveYModifier[4]; // TODO:
	private final MoveYModifier[] mSubBtnMoveIn = new MoveYModifier[4];
	private final IEntityModifierListener[] mSubBtnMoveInListener = new IEntityModifierListener[4];
	private Sound mSound = null;

	// ===========================================================
	// Constructors
	// ===========================================================
	public ExpandableButtonGroup(final float pX, final float pY,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		this(pX, pY, pTextureRegionBg.getWidth(), pTextureRegionBg.getHeight(),
				pTextureRegionBg, pTextureRegionFg, pVertexBufferObjectManager);
	}

	public ExpandableButtonGroup(final float pX, final float pY,
			final float pWidth, final float pHeight,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		super(pX, pY, pWidth, pHeight);

		if (pTextureRegionBg == null) {
			throw new IllegalArgumentException(
					"Background texture region can not be null.");
		} else if (pTextureRegionFg == null) {
			throw new IllegalArgumentException(
					"Foreground texture region can not be null.");
		} else if (pVertexBufferObjectManager == null) {
			throw new IllegalArgumentException(
					"Vertex buffer object manager can not be null.");
		}

		/* containers */
		mSubButtons = new SparseArray<SubButton>(CAPACITY_DEFAULT);
		mPendingTouchAreas = new ArrayList<ITouchArea>(CAPACITY_DEFAULT);

		/* main button bg stuff */
		mMainBtnBgRegion = pTextureRegionBg;
		mMainBtnBgSprite = new MainBtnBgSprite(pWidth / 2, pHeight / 2, pWidth,
				pHeight, pTextureRegionBg, pVertexBufferObjectManager);
		mMainBtnBgSprite.setZIndex(ZINDEX_MAIN_BUTTON_BG);
		this.attachChild(mMainBtnBgSprite);
		mPendingTouchAreas.add(mMainBtnBgSprite);

		/* main button fg stuff */
		mMainBtnFgSprite = new Sprite(mMainBtnBgSprite.getWidth() / 2,
				mMainBtnBgSprite.getHeight() / 2, pTextureRegionFg,
				pVertexBufferObjectManager);
		mMainBtnFgSprite.setZIndex(ZINDEX_MAIN_BUTTON_FG);
		mMainBtnBgSprite.attachChild(mMainBtnFgSprite);

		/* pre-defined entity modifier listener */
		mMainBtnRotationListener = new IEntityModifierListener() {
			@Override
			public void onModifierStarted(IModifier<IEntity> pModifier,
					IEntity pItem) {
				ExpandableButtonGroup.this.mMainBtnRotating = true;
			}

			@Override
			public void onModifierFinished(IModifier<IEntity> pModifier,
					IEntity pItem) {
				ExpandableButtonGroup.this.mMainBtnRotating = false;
			}
		};

		for (int i = 0; i < 4; i++) {
			final int idx = i;
			mSubBtnMoveInListener[i] = new IEntityModifierListener() {
				@Override
				public void onModifierStarted(IModifier<IEntity> pModifier,
						IEntity pItem) {
				}

				@Override
				public void onModifierFinished(IModifier<IEntity> pModifier,
						IEntity pItem) {
					SubButton subBtn = mSubButtons.valueAt(idx);
					if (subBtn != null) {
						subBtn.setChildrenIgnoreUpdate(true);
						subBtn.setChildrenVisible(false);
					}
				}
			};
		}

		/* pre-defined entity modifiers for main btn */
		mMainBtnRotationCW = new RotationModifier(MAIN_BTN_ROTATION_DURATION,
				0, MAIN_BTN_ROTATION_DEGREE, mMainBtnRotationListener);
		mMainBtnRotationCW.setAutoUnregisterWhenFinished(true);
		mMainBtnRotationCCW = new RotationModifier(MAIN_BTN_ROTATION_DURATION,
				MAIN_BTN_ROTATION_DEGREE, 0, mMainBtnRotationListener);
		mMainBtnRotationCCW.setAutoUnregisterWhenFinished(true);

		/* pre-defined entity modifiers for sub btns */
		for (int i = 0; i < 4; i++) {
			mSubBtnMoveOut[i] = new MoveYModifier(SUB_BTN_MOVE_DURATION,
					mMainBtnBgSprite.getHeight() / 2,
					mMainBtnBgSprite.getHeight() * (i + 1.5f));
			mSubBtnMoveOut[i].setAutoUnregisterWhenFinished(true);
			mSubBtnMoveIn[i] = new MoveYModifier(SUB_BTN_MOVE_DURATION,
					mMainBtnBgSprite.getHeight() * (i + 1.5f),
					mMainBtnBgSprite.getHeight() / 2, mSubBtnMoveInListener[i]);
			mSubBtnMoveIn[i].setAutoUnregisterWhenFinished(true);
		}

	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================
	public void SetSound(Sound pSound) {
		this.mSound = pSound;
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================
	@Override
	public void onAttached() {

		IEntity parent = this.getParent();
		if (!(parent instanceof Scene)) {
			throw new IllegalStateException("Can only be attached on Scene.");
		}

		/* register touch areas */
		if (!mPendingTouchAreas.isEmpty()) {
			Scene parentScene = (Scene) parent;
			for (ITouchArea area : mPendingTouchAreas) {
				parentScene.registerTouchArea(area);
			}
			mPendingTouchAreas.clear();
		}
	}

	@Override
	public void onDetached() {
		// TODO: unregister touch areas
	}

	// ===========================================================
	// Methods
	// ===========================================================
	public void AddButton(final int pButtonId,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {

		if (pTextureRegionFg == null) {
			throw new IllegalArgumentException(
					"Foreground texture region can not be null.");
		}

		if (pVertexBufferObjectManager == null) {
			throw new IllegalArgumentException(
					"Vertex buffer object manager can not be null.");
		}

		if (mSubButtons.indexOfKey(pButtonId) >= 0) {
			throw new IllegalArgumentException("Button id (" + pButtonId
					+ ") is used, choose others...");
		}

		final SubButton subBtn = new SubButton(pButtonId, -1);
		subBtn.setChildrenIgnoreUpdate(true);
		subBtn.setChildrenVisible(false);
		this.attachChild(subBtn);

		final Sprite btnBg = new Sprite(this.getWidth() / 2,
				this.getHeight() / 2,
				(pTextureRegionBg != null) ? pTextureRegionBg
						: this.mMainBtnBgRegion, pVertexBufferObjectManager);
		if (pTextureRegionBg == null)
			btnBg.setScale(0.75f);
		btnBg.setZIndex(ZINDEX_SUB_BUTTON_BG);
		subBtn.attachChild(btnBg);

		final Sprite btnFg = new Sprite(btnBg.getWidth() / 2,
				btnBg.getHeight() / 2, pTextureRegionFg,
				pVertexBufferObjectManager);
		btnFg.setZIndex(ZINDEX_SUB_BUTTON_FG);
		btnBg.attachChild(btnFg);

		sortChildren();
		mSubButtons.put(pButtonId, subBtn);
		registerTouchAreaOnParentScene(subBtn);
	}

	public void AddToggleButon(final int pButtonId, final int pCurrentIndex,
			final ITiledTextureRegion pTiledTextureRegionBg,
			final ITiledTextureRegion pTiledTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {

		if (pTiledTextureRegionFg == null) {
			throw new IllegalArgumentException(
					"Foreground texture region can not be null.");
		}

		if (pVertexBufferObjectManager == null) {
			throw new IllegalArgumentException(
					"Vertex buffer object manager can not be null.");
		}

		if (mSubButtons.indexOfKey(pButtonId) >= 0) {
			throw new IllegalArgumentException("Button id (" + pButtonId
					+ ") is used, choose others...");
		}

		final SubButton subBtn = new SubButton(pButtonId, pCurrentIndex);
		subBtn.setChildrenIgnoreUpdate(true);
		subBtn.setChildrenVisible(false);
		this.attachChild(subBtn);

		final Sprite btnBg;
		if (pTiledTextureRegionBg != null) {
			btnBg = new TiledSprite(0, 0, pTiledTextureRegionBg,
					pVertexBufferObjectManager);
			((TiledSprite) btnBg).setCurrentTileIndex(pCurrentIndex);
		} else {
			btnBg = new Sprite(0, 0, this.mMainBtnBgRegion,
					pVertexBufferObjectManager);
		}
		btnBg.setZIndex(ZINDEX_SUB_BUTTON_BG);
		subBtn.attachChild(btnBg);

		final TiledSprite btnFg = new TiledSprite(btnBg.getWidth() / 2,
				btnBg.getHeight() / 2, pTiledTextureRegionFg,
				pVertexBufferObjectManager);
		btnFg.setZIndex(ZINDEX_SUB_BUTTON_FG);
		btnFg.setCurrentTileIndex(pCurrentIndex);
		btnBg.attachChild(btnFg);

		sortChildren();
		mSubButtons.put(pButtonId, subBtn);
		registerTouchAreaOnParentScene(btnBg);
	}

	public void setOnSubButtonClickListener(
			final IOnSubButtonClickListener pOnSubButtonClickListener) {
		this.mOnSubButtonClickListener = pOnSubButtonClickListener;
	}

	private void registerTouchAreaOnParentScene(ITouchArea pTouchArea) {
		IEntity parent = this.getParent();
		if (parent != null) {
			Scene parentScene = (Scene) parent;
			parentScene.registerTouchArea(pTouchArea);
		} else {
			mPendingTouchAreas.add(pTouchArea);
		}
	}

	private void soundPlay() {
		if (mSound != null)
			mSound.play();
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
	private class MainBtnBgSprite extends Sprite {
		public MainBtnBgSprite(final float pX, final float pY,
				final float pWidth, final float pHeight,
				final ITextureRegion pTextureRegion,
				final VertexBufferObjectManager pVertexBufferObjectManager) {
			super(pX, pY, pWidth, pHeight, pTextureRegion,
					pVertexBufferObjectManager);
		}

		@Override
		public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
				float pTouchAreaLocalX, float pTouchAreaLocalY) {

			int action = pSceneTouchEvent.getAction();
			switch (action) {
			case TouchEvent.ACTION_DOWN:
				this.setScale(1.3f);
				break;
			case TouchEvent.ACTION_MOVE:
				/* do nothing */
				break;
			case TouchEvent.ACTION_UP:
				/* scale down */
				this.setScale(1.0f);

				/* sound feedback */
				ExpandableButtonGroup.this.soundPlay();

				/* skip if busy */
				if (mMainBtnRotating)
					break;

				int subBtnCnt = mSubButtons.size();
				SubButton subBtn;
				if (mExpanded) {
					mExpanded = false;
					/* main btn ccw rotation */
					mMainBtnRotationCCW.reset();
					mMainBtnFgSprite
							.registerEntityModifier(mMainBtnRotationCCW);
					/* sub btns close in */
					for (int i = 0; i < subBtnCnt; i++) {
						subBtn = mSubButtons.valueAt(i);
						mSubBtnMoveIn[i].reset();
						subBtn.registerEntityModifier(mSubBtnMoveIn[i]);
					}
				} else {
					mExpanded = true;
					/* main btn cw rotation */
					mMainBtnRotationCW.reset();
					mMainBtnFgSprite.registerEntityModifier(mMainBtnRotationCW);
					/* sub btns expand out */
					for (int i = 0; i < subBtnCnt; i++) {
						subBtn = mSubButtons.valueAt(i);
						subBtn.setChildrenIgnoreUpdate(false);
						subBtn.setChildrenVisible(true);
						mSubBtnMoveOut[i].reset();
						subBtn.registerEntityModifier(mSubBtnMoveOut[i]);
					}
				}
				break;
			default:
				Log.w(TAG, "Unexpected action(" + action
						+ ") happens in main button @ ExpandableButtonGroup.");
				break;
			}

			return true;
		}
	}

	private class SubButton extends Entity implements ISubButton {

		private final int mId;
		private int mIndex;

		public SubButton(final int pId, final int pIndex) {
			super(ExpandableButtonGroup.this.getWidth() * 0.5f,
					ExpandableButtonGroup.this.getHeight() * 0.5f);
			mId = pId;
			mIndex = pIndex;
		}

		@Override
		public void attachChild(IEntity pEntity) throws IllegalStateException {
			super.attachChild(pEntity);

			if (this.mChildren != null && this.mChildren.size() == 1) {
				IEntity firstChild = this.getFirstChild();
				this.setWidth(firstChild.getWidth());
				this.setHeight(firstChild.getHeight());
			}
		}

		@Override
		public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
				float pTouchAreaLocalX, float pTouchAreaLocalY) {

			int action = pSceneTouchEvent.getAction();
			switch (action) {
			case TouchEvent.ACTION_DOWN:
				/* scale up */
				this.setScale(1.3f);
				break;
			case TouchEvent.ACTION_MOVE:
				/* do nothing */
				break;
			case TouchEvent.ACTION_UP:
				/* scale down */
				this.setScale(1.0f);

				/* sound feedback */
				ExpandableButtonGroup.this.soundPlay();

				/* skip if busy */
				if (mMainBtnRotating) // TODO: add self animation flag..
					break;

				if (ExpandableButtonGroup.this.mOnSubButtonClickListener != null)
					ExpandableButtonGroup.this.mOnSubButtonClickListener
							.onSubButtonClicked(this);
				break;
			default:
				Log.w(TAG, "Unexpected action(" + action
						+ ") happens in ExpandableButtonGroup.onAreaTouched.");
				break;
			}

			return true;
		}

		@Override
		public int getId() {
			return mId;
		}

		@Override
		public int getCurrentIndex() {
			return mIndex;
		}

		@Override
		public void setCurrentIndex(final int pIndex) {

			mIndex = pIndex;
			IEntity firstChild = this.getFirstChild();

			if (firstChild != null) {
				if (firstChild instanceof TiledSprite) {
					((TiledSprite) firstChild).setCurrentTileIndex(mIndex);
				}
				IEntity firstGrandChild = firstChild.getFirstChild();
				if (firstGrandChild != null
						&& firstGrandChild instanceof TiledSprite) {
					((TiledSprite) firstGrandChild).setCurrentTileIndex(mIndex);
				}
			}
		}

		@Override
		public boolean isMultiState() {

			boolean multiState = false;

			IEntity firstGrandChild, firstChild = this.getFirstChild();
			if (firstChild != null) {
				firstGrandChild = firstChild.getFirstChild();
				if (firstGrandChild != null
						&& firstGrandChild instanceof TiledSprite)
					multiState = true;
			}

			return multiState;
		}
	}

	public interface ISubButton {
		int getId();

		boolean isMultiState();

		int getCurrentIndex();

		void setCurrentIndex(final int pIndex);
	}

	public interface IOnSubButtonClickListener {
		boolean onSubButtonClicked(final ISubButton pSubButton);
	}
}
