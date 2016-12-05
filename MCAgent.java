package ch.idsia.agents;
import java.util.*;
import java.math.*;

import ch.idsia.agents.controllers.BasicMarioAIAgent;
import ch.idsia.benchmark.mario.engine.sprites.Mario;
import ch.idsia.benchmark.mario.engine.sprites.Sprite;
import ch.idsia.benchmark.mario.environments.*;
import ch.idsia.tools.MarioAIOptions;
import ch.idsia.agents.KeyOfMC;


public class MCAgent extends BasicMarioAIAgent implements Agent{
	static String name = "MCAgent";
	
	//前方2マスの縦何マスを取得するか
	public static final int width = 4;
	//ステージの距離100ごとの数
	public static final int numOfDistanceByFourHundred = 11;
	//取り得る行動の数
	public static final int numOfAction = 7;
	//J：ジャンプ　S：ファイア　R：右　D：下
	/*enum Action{
	 * S,
	  	R,
	  	L,
		JR,
		SR,
		JS,
		JSR
		
	}*/
	//毎フレームもっとも価値の高い行動をするが、確率epsilonで他の行動を等確率で選択
	public static  float epsilon = 0.05f;
	public static float epsilonX = 0.01f;
	
	//もっとも良い選択の再現に使用
	//private static int frameCounter = 0;
	//もっとも良い選択の再現に使用
	private static int frameCounter = 0;
	
	//学習中にもっとも良かった行動群
	//public static List<Integer> best;
	//毎エピソードで選択した行動を全フレーム分とっておく
	public static List<Integer> actions;
	//学習中にもっとも良かった行動群
	public static List<Integer> best;
	//400ごとのbestモード、-1＝未到達、0＝small,1=large,2=fire
	private int[] modeByFourHundred;
	//学習中にもっとも良かったスコア
	public static float bestScore;
	//マリオの周りの状態とマリオが地面についているか
	private static int state = 0;
	//前1マスに崖があるか 0 : ない 1 : ある
	private static int cliff = 0;
	//前1マスに壁があるか 0 : ない 1 : ある
	public static int wall = 0;
	//よく死ぬ位置
	public static int deadPoint = 0;
	//marioの進んだ距離/400
	private static int distanceByFourHundred = 0;
	private static int lastDistanceByFourHundred = 0;
	//bestScore/400
	private static int bestDistanceByFourHundred = 0;
	//マリオがジャンプできるか 0 : できない 1 : できる
	private static int ableToJump = 0;
	//毎フレームで貪欲な選択をするかどうか
	public static boolean mode = false;
	//各エピソードで、ある状態である行動を取ったかどうか KeyOfMCはint4つでstate,cliff,ableToJump,action
	//valueのIntegerはこのMCでは使わない
	public static HashMap<KeyOfMC,Integer> selected;
	//行動価値関数　これを基に行動を決める
	public static float[][][][][] qValue;
	//各状態行動対におけるそれまで得た報酬の合計
	public static float[][][][][] sumValue;
	//ある状態である行動を取った回数
	public static int[][][][][] num;
	public static void setMode(boolean b){
		mode = b;
	}
	public static void setDeadPoint(int point){
		deadPoint = point;
	}
	public static void ini(){
		frameCounter = 0;
		selected.clear();
		for(int i =0;i<numOfDistanceByFourHundred;i++){
			actions.clear();
		}
	}
	//使わない
	/*
	public static void setPolicy(){
		for(int i= 0; i < (int)Math.pow(2.0,4 * width + 1); ++i){
			for(int j = 0; j < 2; ++j){
				for(int k = 0; k < 2; ++k){
					float r = (float)(Math.random());
					int idx = 0;
					if(r < epsilon){
						float sum = 0;
						float d = epsilon / (float)numOfAction;
						sum += d;
						while(sum < r){
							sum += d;
							idx++;
						}
					}else{
						float max = -Float.MAX_VALUE;
						for(int t = 0; t < numOfAction; ++t){
							float q = qValue[state][cliff][ableToJump][t];
							if(q > max){
								max = q;
								idx = t;
							}
						}
					}
				}
			}
		}
	}
	*/
	//コンストラクタ
	public MCAgent(){
		super(name);
		qValue = new float[(int)Math.pow(2.0,4 * width + 1)][2][2][numOfAction][numOfDistanceByFourHundred];
		sumValue = new float[(int)Math.pow(2.0,4 * width  + 1)][2][2][numOfAction][numOfDistanceByFourHundred];
		num = new int[(int)Math.pow(2.0,4 * width + 1)][2][2][numOfAction][numOfDistanceByFourHundred];
		selected = new HashMap<KeyOfMC,Integer>();
		for(int i = 0; i < (int)Math.pow(2.0,4 * width + 1); ++i){
			for(int j = 0; j < 2; ++j){
				for(int k = 0; k < 2; ++k){
					for(int t = 0; t < numOfAction; ++t){
						for(int s=0;s<numOfDistanceByFourHundred;s++){
							qValue[i][j][k][t][s] = 0.0f;
							//一応全パターンは1回は試したいのである程度の値は持たせる
							sumValue[i][j][k][t][s] = 4096.0f;
							num[i][k][k][t][s] = 1;
						}
					}
				}
			}
		}
		
		//best = new ArrayList<Integer>();
		actions = new ArrayList<Integer>();
		best = new ArrayList<Integer>();
		modeByFourHundred = new int[numOfDistanceByFourHundred];
		for(int i = 0;i<numOfDistanceByFourHundred;i++){
			modeByFourHundred[i] = -1;
		}
	}
	//行動価値関数を取得
	public static float[][][][][] getQ(){
		return qValue;
	}
	//行動価値関数を取得
	//学習した後に再現で使う
	public static void setQ(float[][][][][] q){
		qValue = q;
	}
	public static void setVestDistanceByHundred(int distance){
		bestDistanceByFourHundred = distance/400;
	}
	//
	public static void controllEpsilon(){
		if(distanceByFourHundred+1 >= bestDistanceByFourHundred ) epsilon = 0.3f;
		else epsilon = 0.05f;
	}
	//障害物を検出し、stateの各bitに0,1で格納
	//ここでマリオが得る情報をほとんど決めている
	//ついでにマリオが地面にいるかも取得
	public void detectObstacle(){
		state = 0;
		for(int j = 0; j < width; ++j){
			if(getEnemiesCellValue(marioEgoRow + j - 1,marioEgoCol + 1) != Sprite.KIND_NONE)
				state += (int)Math.pow(2,j);
		}
		for(int j = 0; j < width; ++j){
			if(getReceptiveFieldCellValue(marioEgoRow + j - 1,marioEgoCol + 1) != 0)
				state += (int)Math.pow(2,width + j);
		}
		for(int j = 0; j < width; ++j){
			if(getEnemiesCellValue(marioEgoRow + j - 1,marioEgoCol + 2) != Sprite.KIND_NONE)
				state += (int)Math.pow(2, 2 * width + j);
		}
		for(int j = 0; j < width; ++j){
			if(getReceptiveFieldCellValue(marioEgoRow + j - 1,marioEgoCol + 2) != 0)
				state += (int)Math.pow(2,3 * width + j);
		}
		if(isMarioOnGround)
			state += (int)Math.pow(2, 4 * width);
	}
	//boolをintへ
	public int boolToInt(boolean b){
		return (b) ? 1 : 0;
	}
	//崖検出
	public void detectCliff(){
		
		boolean b = true;
		for(int i = 0; i < 10; ++i){
			if(getReceptiveFieldCellValue(marioEgoRow + i,marioEgoCol + 1) != 0){
				b = false;
				break;
			}
		}
		cliff = (b) ? 1 : 0;
	}
	
	
	//ソフトマックス手法
	//使わない
	/*
	public int chooseActionS(){
		float sum = 0.0f;
		int idx = 0;
		for(int i = 0; i < numOfAction; ++i){
			sum += Math.pow(Math.E,qValue[state][cliff][ableToJump][i] / 25f);
		}
		float r = (float)(Math.random());
		float f = 0.0f;
		for(int i = 0; i < numOfAction; ++i){
			f += Math.pow(Math.E,qValue[state][cliff][ableToJump][i] / 25f) / sum;
			if(f > r){
				idx = i;
				break;
			}
		}
		return idx;
	}*/
	//行動価値関数を基に行動選択
	public int chooseAction(){
		float r = (float)(Math.random());
		int idx = 0;
		if(r < epsilon){
			float sum = 0;
			float d = epsilon / (float)numOfAction;
			sum += d;
			while(sum < r){
				sum += d;
				idx++;
			}
		}else{
			float max = -Float.MAX_VALUE;
			for(int i = 0; i < numOfAction; ++i){
				float q = qValue[state][cliff][ableToJump][i][distanceByFourHundred];
				if(q > max){
					max = q;
					idx = i;
				}
			}
		}
		return idx;
	}
	//貪欲に行動を選択
	public int chooseActionG(){
		int idx = 0;
		float max = -Float.MAX_VALUE;
		for(int i = 0; i < numOfAction; ++i){
			float q = qValue[state][cliff][ableToJump][i][distanceByFourHundred];
			if(q > max){
				max = q;
				idx = i;
			}
		}
		return idx;
	}
	//deadPointを越えるまでの行動を選択
	public int chooseActionX(){
		float r = (float)(Math.random());
		int idx = 0;
		if(r < epsilonX){
			idx = chooseAction();
		}else{
			idx = best.get(frameCounter);
		}
		return idx;
	}
	//行動選択前にactionを一旦全部falseにする
	public void clearAction(){
		for(int i = 0; i < Environment.numberOfKeys; ++i){
			action[i] = false;
		}
	}
	//int(0-6)をacitonにする
	public void intToAction(int n){
		if(n == 2 || n == 4 || n==5)
			action[Mario.KEY_JUMP] = true;
		if(n == 0 || n == 2 ||n == 5)
			action[Mario.KEY_SPEED] = true;
		if(n == 1 || n == 3 ||n == 4||n == 5)
			action[Mario.KEY_RIGHT] = true;
		if(n == 6)
			action[Mario.KEY_LEFT] = true;
	}
	public boolean[] getAction(){
		detectObstacle();
		detectCliff();
		int mCol = (int)super.marioFloatPos[0];
		distanceByFourHundred = mCol/400;
		
		controllEpsilon();
		ableToJump = boolToInt(isMarioAbleToJump);
		clearAction();
		int currAction = 0;
	    
		if(!mode){
			
			
			if( frameCounter <best.size() && mCol < deadPoint) currAction = chooseActionX();
			else currAction = chooseAction();
			actions.add(currAction);
			intToAction(currAction);
			if(!selected.containsKey(new KeyOfMC(state,cliff,ableToJump,currAction,distanceByFourHundred)))
				selected.put(new KeyOfMC(state,cliff,ableToJump,currAction,distanceByFourHundred),1);	
			else
				selected.put(new KeyOfMC(state,cliff,ableToJump,currAction,distanceByFourHundred), selected.get(new KeyOfMC(state,cliff,ableToJump,currAction,distanceByFourHundred)) + 1);
		}
		else{
			//currAction = chooseActionG();
			if(frameCounter < best.size())
				currAction = best.get(frameCounter);
			else currAction = chooseAction();
			intToAction(currAction);
		}
		frameCounter++;
		return action;
	}
}
