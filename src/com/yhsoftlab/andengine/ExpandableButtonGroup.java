package com.yhsoftlab.andengine;

import java.util.ArrayList;

import org.andengine.audio.sound.Sound;
import org.andengine.entity.Entity;
import org.andengine.entity.IEntity;
import org.andengine.entity.modifier.CascadingAlphaModifier;
import org.andengine.entity.modifier.IEntityModifier.IEntityModifierListener;
import org.andengine.entity.modifier.MoveYModifier;
import org.andengine.entity.modifier.ParallelEntityModifier;
import org.andengine.entity.modifier.RotationModifier;
import org.andengine.entity.modifier.ScaleModifier;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.entity.scene.ITouchArea;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.sprite.Sprite;
import org.andengine.entity.sprite.TiledSprite;
import org.andengine.entity.text.Text;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.opengl.texture.region.ITiledTextureRegion;
import org.andengine.opengl.vbo.VertexBufferObjectManager;
import org.andengine.util.modifier.IModifier;

import android.util.Log;
import android.util.SparseArray;

/**
 * <p>
 * ExpandableButtonGroup mimics the button operation used in Angry birds, Bad
 * Piggies series games.
 * <p>
 * Initially, there is a main sprite button (when constructor is called), and
 * several other sub sprite buttons located behide the main button (when
 * AddButton is called). When main sprite button is clicked, all sub sprite
 * buttons popup with some animation effect, and then, all sub sprite buttons is
 * clickable. Application need to implement the IOnSubButtonClickListener
 * interface to receive the onSubButtonClicked callback method. When main sprite
 * button is clicked again, all sub sprite buttons closed.
 * <p>
 * Note that, this code is based on AndEngine AnchorCenter branch, and did not
 * test on GLES2 branch, so it is expected if it didn't work on GLES2 branch.
 * 
 * <pre>
 * Parent-children relationship:
 * ExpandableButtonGroup
 *    L MainBtnBgSprite => handle touch events. entity modifier target.
 *       L MainBtnFgSprite
 *    L SubButton => handle touch events.
 *       L SubBtnBgSprite => sprite entity modifer target.
 *          L SubBtnFgSprite
 *       L Rectangle => (optional) for better child text recognize. entity modifier target.
 *          L Text => (optional) text description for this sub button.
 *    L another SubButton
 *       ...
 *       ...
 *       ...
 *    L another SubButton
 * </pre>
 */

public class ExpandableButtonGroup extends Entity {

	// ===========================================================
	// Enumeration
	// ===========================================================
	public enum ExpandDirection {
		UP, DOWN;
	}

	public enum TextExpandDirection {
		LEFT, RIGHT;
	}

	// ===========================================================
	// Constants
	// ===========================================================
	private final String TAG = this.getClass().getSimpleName();
	private final int INVALID_INDEX = -1;
	private final int CAPACITY_DEFAULT = 4;
	private final int ZINDEX_SUB_BUTTON_START = 1;
	private final int ZINDEX_MAIN_BUTTON = Integer.MAX_VALUE;
	private final float MAIN_BTN_ROTATION_DURATION = 0.3f;
	private final float MAIN_BTN_ROTATION_DEGREE = 180.0f;

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
	private Sound mSound = null;
	private int mSubButtonsZIndex = ZINDEX_SUB_BUTTON_START;
	private int[] mExpandDirectionCount;

	// ===========================================================
	// Constructors
	// ===========================================================
	/**
	 * Constructor of ExpandableButtonGroup
	 * 
	 * @param pX
	 *            x coordinate of the anchor center point
	 * @param pY
	 *            y coordinate of the anchor center point
	 * @param pTextureRegionBg
	 *            TextureRegion used by background of the main button
	 * @param pTextureRegionFg
	 *            TextureRegion used by foreground of the main button
	 * @param pVertexBufferObjectManager
	 *            VBO manager
	 */
	public ExpandableButtonGroup(final float pX, final float pY,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		this(pX, pY, pTextureRegionBg.getWidth(), pTextureRegionBg.getHeight(),
				pTextureRegionBg, pTextureRegionFg, pVertexBufferObjectManager);
	}

	/**
	 * 
	 * @param pX
	 *            x coordinate of the anchor center point
	 * @param pY
	 *            y coordinate of the anchor center point
	 * @param pWidth
	 *            The width of the main button background. Usually, you don't
	 *            need assign it unless you need to scale up/down the main
	 *            button.
	 * @param pHeight
	 *            The height of the main button background. Usually, you don't
	 *            need assign it unless you need to scale up/down the main
	 *            button.
	 * @param pTextureRegionBg
	 *            TextureRegion used by background of the main button
	 * @param pTextureRegionFg
	 *            TextureRegion used by foreground of the main button
	 * @param pVertexBufferObjectManager
	 *            VBO manager
	 */
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

		/* counter for sub button */
		mExpandDirectionCount = new int[ExpandDirection.values().length];

		/* main button bg stuff */
		mMainBtnBgRegion = pTextureRegionBg;
		mMainBtnBgSprite = new MainBtnBgSprite(pWidth / 2, pHeight / 2, pWidth,
				pHeight, pTextureRegionBg, pVertexBufferObjectManager);
		mMainBtnBgSprite.setZIndex(ZINDEX_MAIN_BUTTON);
		this.attachChild(mMainBtnBgSprite);
		mPendingTouchAreas.add(mMainBtnBgSprite);

		/* main button fg stuff */
		mMainBtnFgSprite = new Sprite(mMainBtnBgSprite.getWidth() / 2,
				mMainBtnBgSprite.getHeight() / 2, pTextureRegionFg,
				pVertexBufferObjectManager);
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

		/* pre-defined entity modifiers for main btn */
		mMainBtnRotationCW = new RotationModifier(MAIN_BTN_ROTATION_DURATION,
				0, MAIN_BTN_ROTATION_DEGREE, mMainBtnRotationListener);
		mMainBtnRotationCW.setAutoUnregisterWhenFinished(true);
		mMainBtnRotationCCW = new RotationModifier(MAIN_BTN_ROTATION_DURATION,
				MAIN_BTN_ROTATION_DEGREE, 0, mMainBtnRotationListener);
		mMainBtnRotationCCW.setAutoUnregisterWhenFinished(true);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================
	/**
	 * Control the sound feedback when any button is clicked.
	 * 
	 * @param pSound
	 *            The {@link Sound} object to play when button's ACTION_UP event
	 *            happened. Set this null to disable sound feedback.
	 */
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
		AddButton(pButtonId, INVALID_INDEX, ExpandDirection.UP, null, null,
				pTextureRegionBg, pTextureRegionFg, pVertexBufferObjectManager);
	}

	public void AddButton(final int pButtonId,
			final ExpandDirection pExpandDirection,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		AddButton(pButtonId, INVALID_INDEX, pExpandDirection, null, null,
				pTextureRegionBg, pTextureRegionFg, pVertexBufferObjectManager);
	}

	public void AddButton(final int pButtonId, final Text pText,
			final TextExpandDirection pTextExpandDirection,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		AddButton(pButtonId, INVALID_INDEX, ExpandDirection.UP, pText,
				pTextExpandDirection, pTextureRegionBg, pTextureRegionFg,
				pVertexBufferObjectManager);
	}

	public void AddButton(final int pButtonId,
			final ExpandDirection pExpandDirection, final Text pText,
			final TextExpandDirection pTextExpandDirection,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		AddButton(pButtonId, INVALID_INDEX, pExpandDirection, pText,
				pTextExpandDirection, pTextureRegionBg, pTextureRegionFg,
				pVertexBufferObjectManager);
	}

	public void AddButton(final int pButtonId, final int pCurrentIndex,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		AddButton(pButtonId, pCurrentIndex, ExpandDirection.UP, null, null,
				pTextureRegionBg, pTextureRegionFg, pVertexBufferObjectManager);
	}

	public void AddButton(final int pButtonId, final int pCurrentIndex,
			final ExpandDirection pExpandDirection,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		AddButton(pButtonId, pCurrentIndex, pExpandDirection, null, null,
				pTextureRegionBg, pTextureRegionFg, pVertexBufferObjectManager);
	}

	public void AddButton(final int pButtonId, final int pCurrentIndex,
			final Text pText, final TextExpandDirection pTextExpandDirection,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {
		AddButton(pButtonId, pCurrentIndex, ExpandDirection.UP, pText,
				pTextExpandDirection, pTextureRegionBg, pTextureRegionFg,
				pVertexBufferObjectManager);
	}

	/**
	 * Add sub button.
	 * 
	 * @param pButtonId
	 *            The sub button id. The id should be unique in the same
	 *            ExpandableButtonGroup.
	 * @param pCurrentIndex
	 *            The index of the current state. Set to INVALID_INDEX if this
	 *            is not a multi-state button.
	 * @param pExpandDirection
	 *            The pop up direction when this sub button is about to appear.
	 * @param pText
	 *            The optional text description to be displayed next to the sub
	 *            button.
	 * @param pTextExpandDirection
	 *            The text expand direction related to the sub button.
	 * @param pTextureRegionBg
	 *            This is used by background of the sub button. If this is a
	 *            multi-state sub button (pCurrentIndex != INVALID_INDEX), the
	 *            field should be a instance of ITiledTextureRegion. If this
	 *            field is null, the same ITextureRegion used by the main button
	 *            will be reused and the size a little scaling down.
	 * @param pTextureRegionFg
	 *            This is used by foreground of the sub button. If this is a
	 *            multi-state sub button (pCurrentIndex != INVALID_INDEX), the
	 *            field should be a instance of ITiledTextureRegion.
	 * @param pVertexBufferObjectManager
	 *            VBO manager.
	 */
	public void AddButton(final int pButtonId, final int pCurrentIndex,
			final ExpandDirection pExpandDirection, final Text pText,
			final TextExpandDirection pTextExpandDirection,
			final ITextureRegion pTextureRegionBg,
			final ITextureRegion pTextureRegionFg,
			final VertexBufferObjectManager pVertexBufferObjectManager) {

		if (pTextureRegionFg == null) {
			throw new IllegalArgumentException(
					"Foreground texture region can not be null.");
		}

		if (pCurrentIndex != INVALID_INDEX
				&& !(pTextureRegionFg instanceof ITiledTextureRegion)) {
			throw new IllegalArgumentException(
					"Foreground texture region should be instance of ITiledTextureRegion when pCurrentIndex is not "
							+ INVALID_INDEX + ".");
		}

		if (pCurrentIndex == INVALID_INDEX
				&& pTextureRegionFg instanceof ITiledTextureRegion) {
			throw new IllegalArgumentException(
					"Foreground texture region should not be instance of ITiledTextureRegion when pCurrentIndex is "
							+ INVALID_INDEX + ".");
		}

		if (pVertexBufferObjectManager == null) {
			throw new IllegalArgumentException(
					"Vertex buffer object manager can not be null.");
		}

		if (mSubButtons.indexOfKey(pButtonId) >= 0) {
			throw new IllegalArgumentException("Button id (" + pButtonId
					+ ") is used, choose others...");
		}

		final SubButton subBtn = new SubButton(pButtonId, pCurrentIndex,
				pExpandDirection, pTextExpandDirection);
		subBtn.setChildrenIgnoreUpdate(true);
		subBtn.setChildrenVisible(false);
		subBtn.setZIndex(this.mSubButtonsZIndex++);
		this.attachChild(subBtn);

		final Sprite btnBg;
		if (pTextureRegionBg != null) {
			if (pTextureRegionBg instanceof ITiledTextureRegion) {
				btnBg = new TiledSprite(pTextureRegionBg.getWidth() * 0.5f,
						pTextureRegionBg.getHeight() * 0.5f,
						(ITiledTextureRegion) pTextureRegionBg,
						pVertexBufferObjectManager);
				final TiledSprite btnTiledBg = (TiledSprite) btnBg;
				if (pCurrentIndex >= 0
						&& pCurrentIndex < btnTiledBg.getTileCount())
					btnTiledBg.setCurrentTileIndex(pCurrentIndex);
				else
					btnTiledBg.setCurrentTileIndex(0);
			} else {
				btnBg = new Sprite(pTextureRegionBg.getWidth() * 0.5f,
						pTextureRegionBg.getHeight() * 0.5f, pTextureRegionBg,
						pVertexBufferObjectManager);
			}
		} else {
			btnBg = new Sprite(this.getWidth() * 0.5f, this.getHeight() * 0.5f,
					this.mMainBtnBgRegion, pVertexBufferObjectManager);
			btnBg.setScale(0.75f);
		}
		subBtn.attachChild(btnBg);

		final Sprite btnFg;
		if (pTextureRegionFg instanceof ITiledTextureRegion) {
			btnFg = new TiledSprite(btnBg.getWidth() * 0.5f,
					btnBg.getHeight() * 0.5f,
					(ITiledTextureRegion) pTextureRegionFg,
					pVertexBufferObjectManager);
			final TiledSprite btnTiledFg = (TiledSprite) btnFg;
			if (pCurrentIndex >= 0 && pCurrentIndex < btnTiledFg.getTileCount())
				btnTiledFg.setCurrentTileIndex(pCurrentIndex);
			else
				btnTiledFg.setCurrentTileIndex(0);
		} else {
			btnFg = new Sprite(btnBg.getWidth() * 0.5f,
					btnBg.getHeight() * 0.5f, pTextureRegionFg,
					pVertexBufferObjectManager);
		}
		btnBg.attachChild(btnFg);

		if (pText != null && pTextExpandDirection != null) {
			int posNeg = (pTextExpandDirection == TextExpandDirection.RIGHT) ? 1
					: -1;
			pText.setPosition(pText.getWidth() * 0.5f, pText.getHeight() * 0.5f);
			pText.setAlpha(0);
			Rectangle rect = new Rectangle(btnBg.getX() + posNeg * 0.5f
					* (btnBg.getWidth() * 1.2f + pText.getWidth()),
					btnBg.getY(), pText.getWidth(), pText.getHeight(),
					pVertexBufferObjectManager);
			rect.setColor(0, 0, 0, 0);
			rect.attachChild(pText);
			subBtn.attachChild(rect);
		}

		sortChildren();
		mSubButtons.put(pButtonId, subBtn);
		registerTouchAreaOnParentScene(subBtn);
	}

	/**
	 * Register IOnSubButtonClickListener
	 * 
	 * @param pOnSubButtonClickListener
	 *            the IOnSubButtonClickListener instance.
	 */
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
				if (mMainBtnRotating) {
					Log.i(TAG, "Main button is busy, skip this one click.");
					break;
				}

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
						subBtn.Show(false);
					}
				} else {
					mExpanded = true;
					/* main btn cw rotation */
					mMainBtnRotationCW.reset();
					mMainBtnFgSprite.registerEntityModifier(mMainBtnRotationCW);
					/* sub btns expand out */
					for (int i = 0; i < subBtnCnt; i++) {
						subBtn = mSubButtons.valueAt(i);
						subBtn.Show(true);
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
		private final float SUB_BTN_ANIMATION_DURATION = 0.3f;
		private final float SUB_BTN_SCALE_MIN = 0.1f;
		private final MoveYModifier mMoveOutModifier;
		private final MoveYModifier mMoveInModifier;
		private final ScaleModifier mScaleOutModifier;
		private final ScaleModifier mScaleInModifier;
		private final ParallelEntityModifier mSpriteOutModifier;
		private final ParallelEntityModifier mSpriteInModifier;
		private final IEntityModifierListener mSpriteModifierListener;
		private final CascadingAlphaModifier mTextOutModifier;
		private final CascadingAlphaModifier mTextInModifier;
		private final IEntityModifierListener mTextModifierListener;
		private boolean mAnimating = false;

		public SubButton(final int pId, final int pIndex,
				final ExpandDirection pExpandDirection,
				final TextExpandDirection pTextExpandDirection) {
			super(ExpandableButtonGroup.this.getWidth() * 0.5f,
					ExpandableButtonGroup.this.getHeight() * 0.5f);
			mId = pId;
			mIndex = pIndex;

			/* optional text in/out modifiers & listener */
			if (pTextExpandDirection != null) {
				mTextOutModifier = new CascadingAlphaModifier(
						SUB_BTN_ANIMATION_DURATION, 0, 1);
				mTextInModifier = new CascadingAlphaModifier(
						SUB_BTN_ANIMATION_DURATION, 1, 0);
				mTextModifierListener = new IEntityModifierListener() {
					@Override
					public void onModifierStarted(IModifier<IEntity> pModifier,
							IEntity pItem) {
						if (pModifier.equals(mTextInModifier)) {
							SubButton.this.mAnimating = true;
						}
					}

					@Override
					public void onModifierFinished(
							IModifier<IEntity> pModifier, IEntity pItem) {
						if (pModifier.equals(mTextOutModifier)) {
							SubButton.this.mAnimating = false;
						} else if (pModifier.equals(mTextInModifier)) {
							SubButton.this.mSpriteInModifier.reset();
							SubButton.this
									.registerEntityModifier(SubButton.this.mSpriteInModifier);
						}
					}
				};
				mTextOutModifier.addModifierListener(mTextModifierListener);
				mTextInModifier.addModifierListener(mTextModifierListener);
			} else {
				mTextOutModifier = null;
				mTextInModifier = null;
				mTextModifierListener = null;
			}

			/* sprite in/out modifiers & listener */
			int subBtnCnt = ExpandableButtonGroup.this.mExpandDirectionCount[pExpandDirection
					.ordinal()]++;
			int posNeg = (pExpandDirection == ExpandDirection.UP) ? 1 : -1;
			mMoveOutModifier = new MoveYModifier(SUB_BTN_ANIMATION_DURATION,
					mMainBtnBgSprite.getX(), mMainBtnBgSprite.getY() + posNeg
							* (subBtnCnt + 1.15f)
							* mMainBtnBgSprite.getHeight());
			mMoveInModifier = new MoveYModifier(SUB_BTN_ANIMATION_DURATION,
					mMainBtnBgSprite.getY() + posNeg * (subBtnCnt + 1.15f)
							* mMainBtnBgSprite.getHeight(),
					mMainBtnBgSprite.getX());
			mScaleOutModifier = new ScaleModifier(SUB_BTN_ANIMATION_DURATION,
					SUB_BTN_SCALE_MIN, 1.0f);
			mScaleInModifier = new ScaleModifier(SUB_BTN_ANIMATION_DURATION,
					1.0f, SUB_BTN_SCALE_MIN);
			mSpriteModifierListener = new IEntityModifierListener() {
				@Override
				public void onModifierStarted(IModifier<IEntity> pModifier,
						IEntity pItem) {
					if (pModifier.equals(mSpriteOutModifier)
							|| (pModifier.equals(mSpriteInModifier) && !SubButton.this
									.containText())) {
						SubButton.this.mAnimating = true;
					}
				}

				@Override
				public void onModifierFinished(IModifier<IEntity> pModifier,
						IEntity pItem) {
					if (pModifier.equals(mSpriteOutModifier)) {
						if (SubButton.this.containText()) {
							if (SubButton.this.getChildCount() == 2) {
								IEntity secondChild = SubButton.this
										.getChildByIndex(1);
								SubButton.this.mTextOutModifier.reset();
								secondChild
										.registerEntityModifier(SubButton.this.mTextOutModifier);
							}
						} else {
							SubButton.this.mAnimating = false;
						}
					} else if (pModifier.equals(mSpriteInModifier)) {
						SubButton.this.mAnimating = false;
						SubButton.this.setChildrenIgnoreUpdate(true);
						SubButton.this.setChildrenVisible(false);
					}
				}
			};
			mSpriteOutModifier = new ParallelEntityModifier(
					mSpriteModifierListener, mMoveOutModifier,
					mScaleOutModifier);
			mSpriteInModifier = new ParallelEntityModifier(
					mSpriteModifierListener, mMoveInModifier, mScaleInModifier);
		}

		public void Show(boolean pShow) {
			if (pShow) {
				this.setChildrenIgnoreUpdate(false);
				this.setChildrenVisible(true);
				mSpriteOutModifier.reset();
				this.registerEntityModifier(mSpriteOutModifier);
			} else {
				if (this.containText()) {
					if (this.getChildCount() == 2) {
						IEntity secondChild = this.getChildByIndex(1);
						mTextInModifier.reset();
						secondChild.registerEntityModifier(mTextInModifier);
					}
				} else {
					mSpriteInModifier.reset();
					this.registerEntityModifier(mSpriteInModifier);
				}
			}
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

			IEntity firstChild = null;
			int action = pSceneTouchEvent.getAction();
			switch (action) {
			case TouchEvent.ACTION_DOWN:
				/* scale up */
				firstChild = this.getFirstChild();
				if (firstChild != null)
					firstChild.setScale(1.3f);
				break;
			case TouchEvent.ACTION_MOVE:
				/* do nothing */
				break;
			case TouchEvent.ACTION_UP:
				/* scale down */
				firstChild = this.getFirstChild();
				if (firstChild != null)
					firstChild.setScale(1.0f);

				/* sound feedback */
				ExpandableButtonGroup.this.soundPlay();

				/* skip if busy */
				if (mAnimating) {
					Log.i(TAG, "SubButton (" + this.mId
							+ ") is busy, skip this one click.");
					break;
				}

				if (ExpandableButtonGroup.this.mOnSubButtonClickListener != null)
					ExpandableButtonGroup.this.mOnSubButtonClickListener
							.onSubButtonClicked(this);
				break;
			default:
				Log.w(TAG, "Unexpected action(" + action
						+ ") happens in SubButton.onAreaTouched.");
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
					final TiledSprite s = (TiledSprite) firstChild;
					if (pIndex < s.getTileCount()) {
						s.setCurrentTileIndex(mIndex);
					}
				}
				IEntity firstGrandChild = firstChild.getFirstChild();
				if (firstGrandChild != null) {
					if (firstGrandChild instanceof TiledSprite) {
						final TiledSprite s = (TiledSprite) firstGrandChild;
						if (pIndex < s.getTileCount()) {
							s.setCurrentTileIndex(mIndex);
						}
					}
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

		@Override
		public boolean containText() {
			if (getChildCount() == 2)
				return true;
			return false;
		}

		@Override
		public void updateText(final CharSequence pCharSequence) {
			if (getChildCount() == 2) {
				IEntity secondChild = getChildByIndex(1);
				IEntity firstGrandChild = secondChild.getFirstChild();
				if (firstGrandChild != null && firstGrandChild instanceof Text) {
					final Text text = (Text) firstGrandChild;
					final CharSequence oldCs = text.getText();
					final float oldWidth = text.getWidth();
					text.setText(pCharSequence);
					/*
					 * update text & rectangle x/width if char sequence length
					 * changed
					 */
					if (oldCs.length() != pCharSequence.length()) {
						final float newWidth = text.getWidth();
						float widthDiff = newWidth - oldWidth;
						text.setPosition(text.getX() + widthDiff * 0.5f,
								text.getY());
						if (secondChild instanceof Rectangle) {
							Rectangle rect = (Rectangle) secondChild;
							final float rectOldWidth = rect.getWidth();
							float rectWidthDiff = newWidth - rectOldWidth;
							rect.setPosition(
									rect.getX() + rectWidthDiff * 0.5f,
									rect.getY());
							rect.setWidth(newWidth);
						}
					}
				}
			}
		}
	}

	public interface ISubButton {

		/**
		 * @return Id of this sub button.
		 */
		int getId();

		/**
		 * @return true if this sub button is multi-state sub button.<br>
		 *         false if this is not.
		 */
		boolean isMultiState();

		/**
		 * Only useful if this is multi-state sub button.
		 * 
		 * @return the current state of this sub button.
		 */
		int getCurrentIndex();

		/**
		 * Only useful if this is multi-state sub button.
		 * 
		 * @param pIndex
		 *            the current state of this sub button.
		 */
		void setCurrentIndex(final int pIndex);

		/**
		 * Check if the sub button contains text or not
		 * 
		 * @return true if this sub button contains text.<br>
		 *         false if it doesn't.
		 */
		boolean containText();

		/**
		 * Only useful if this sub button contains text.
		 * 
		 * @param pCharSequence
		 *            the new text to be displayed.
		 */
		void updateText(final CharSequence pCharSequence);
	}

	public interface IOnSubButtonClickListener {
		boolean onSubButtonClicked(final ISubButton pSubButton);
	}
}
