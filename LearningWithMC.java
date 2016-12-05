package ch.idsia.agents;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import ch.idsia.benchmark.mario.engine.GlobalOptions;
import ch.idsia.benchmark.mario.environments.Environment;
import ch.idsia.benchmark.tasks.BasicTask;
import ch.idsia.benchmark.tasks.LearningTask;
import ch.idsia.tools.EvaluationInfo;
import ch.idsia.tools.MarioAIOptions;
import ch.idsia.utils.wox.serial.Easy;
import ch.idsia.agents.MCAgent;
import ch.idsia.agents.KeyOfMC;

public class LearningWithMC implements LearningAgent{
	private Agent agent;
	private String name = "LearningWithMC";
	//目標値(4096.0はステージの右端)
	private float goal = 4096.0f;
	private String args;
	//試行回数
	private int numOfTrial = 2000000;
	private int deadPoint=0,deadMark = 0,deadMemory = 0,deadMemoryCounter=0,deadMarkCounter = 0,nextDeadPoint = 0,nextDeadPointCounter=0;
	
	
	//コンストラクタ
	public LearningWithMC(String args){
		this.args = args;
		agent = new MCAgent();
	}
	
	//学習部分
	//10000回学習してその中でもっとも良かったものをリプレイ
	public void learn(){
		float best = 0;
		for(int i = 0;i<numOfTrial;i++){
			float run = run();
			//目標値までマリオが到達したらshowして終了
			if(run >= 4096.0f){
				while(true){
					show();
				}
			}
			
			if(i % 1000 == 998){
				System.out.println("bestScoreは" +  MCAgent.bestScore);
				show();
			}
			if(i % 10000 == 9999){
				//10000回bestが更新されないときはnextDeadPointに設定
				if(best == MCAgent.bestScore) setNextDeadPoint((int)best);
				best = MCAgent.bestScore;
				
			}
		}

		try{
			//学習した行動価値関数を書き込み
			File f = new File("MonteCarlo.txt");
			f.createNewFile();
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));
			for(int i = 0; i < Math.pow(2.0, MCAgent.width * 4 + 1); ++i){
				for(int j = 0; j < 2; ++j){
					for(int k = 0;k < 2; ++k){
						for(int t = 0; t < MCAgent.numOfAction; ++t){
							for(int s = 0;s<MCAgent.numOfDistanceByFourHundred;s++){
								bw.write(String.valueOf(MCAgent.qValue[i][j][k][t][s]));
								bw.newLine();
							}
						}
					}
				}
			}
		}
		catch(IOException e){
		    System.out.println(e);
		}
	}
	//リプレイ
	public void show(){
		MCAgent.ini();
		MarioAIOptions marioAIOptions = new MarioAIOptions();
		BasicTask basicTask = new BasicTask(marioAIOptions);

		/* ステージ生成 */
		marioAIOptions.setArgs(this.args);
		MCAgent.setMode(true);


	    /* プレイ画面出力するか否か */
	    marioAIOptions.setVisualization(true);
		/* MCAgentをセット */
		marioAIOptions.setAgent(agent);
		basicTask.setOptionsAndReset(marioAIOptions);

		if ( !basicTask.runSingleEpisode(1) ){
			System.out.println("MarioAI: out of computational time"
			+ " per action! Agent disqualified!");
		}

		/* 評価値(距離)をセット */
		EvaluationInfo evaluationInfo = basicTask.getEvaluationInfo();
		//報酬取得
		float reward = evaluationInfo.distancePassedPhys;
		System.out.println("報酬は" + reward);
	}
	//学習
	//画面に表示はしない
	public float run(){
		MCAgent.ini();
		/* MCAgentをプレイさせる */
		MarioAIOptions marioAIOptions = new MarioAIOptions();
		BasicTask basicTask = new BasicTask(marioAIOptions);

		/* ステージ生成 */
		marioAIOptions.setArgs(this.args);
		MCAgent.setMode(false);


	    /* プレイ画面出力するか否か */
	    marioAIOptions.setVisualization(false);
		/* MCAgentをセット */
		marioAIOptions.setAgent(agent);
		basicTask.setOptionsAndReset(marioAIOptions);

		if ( !basicTask.runSingleEpisode(1) ){
			System.out.println("MarioAI: out of computational time"
			+ " per action! Agent disqualified!");
		}

		/* 評価値(距離)をセット */
		EvaluationInfo evaluationInfo = basicTask.getEvaluationInfo();
		//報酬取得
		float reward = evaluationInfo.distancePassedPhys;
		//reward -= (evaluationInfo.marioStatus == 0) ? 1000 : 0;
		System.out.println(reward);
		deadPointController(reward);
		
		
		Iterator<KeyOfMC> itr = MCAgent.selected.keySet().iterator();
		//価値関数を更新
		
		if(isInErrorRenge(deadPoint,(int)reward) ||isInErrorRenge(nextDeadPoint,(int)reward)) return reward;
		while(itr.hasNext()){
			KeyOfMC key = (KeyOfMC)itr.next();
			MCAgent.sumValue[key.getState()][key.getCliff()][key.getAbleToJump()][key.getAction()][key.getFrameByTwoHundred()]+= reward;
			MCAgent.num[key.getState()][key.getCliff()][key.getAbleToJump()][key.getAction()][key.getFrameByTwoHundred()]++;
			MCAgent.qValue[key.getState()][key.getCliff()][key.getAbleToJump()][key.getAction()][key.getFrameByTwoHundred()] =
					MCAgent.sumValue[key.getState()][key.getCliff()][key.getAbleToJump()][key.getAction()][key.getFrameByTwoHundred()]
							/ (float)MCAgent.num[key.getState()][key.getCliff()][key.getAbleToJump()][key.getAction()][key.getFrameByTwoHundred()];
			
		}
		return reward;
		
	}
	public void deadPointController(float f){
		 int reward = (int) f;
		 int best = (int)MCAgent.bestScore;
		//ベストスコアが出たら更新
		if(f > MCAgent.bestScore){
				MCAgent.bestScore = f;
				MCAgent.setVestDistanceByHundred((int)f);
				
				MCAgent.best = new ArrayList<Integer> (MCAgent.actions);
				
				nextDeadPointCounter = 0;
				if(!isInErrorRenge(reward,best) && !isInErrorRenge(deadPoint,nextDeadPoint) && deadPoint < nextDeadPoint){
					setDeadPoint(nextDeadPoint);
				}
		}
		else if(isInErrorRenge(reward,best)){
			nextDeadPointCounter ++;
			if(nextDeadPointCounter == 7){
				setNextDeadPoint(best);
			}
		}

		else if(!isInErrorRenge(deadPoint,reward) && deadPoint<reward) {
			if(deadMark == reward){
				deadMarkCounter++;
				if(deadMarkCounter == 7){
					
					setDeadPoint(reward);
				}
				return;
			}
			
			if(deadMemory == reward){
				deadMemoryCounter++;
				if(deadMemoryCounter==3){
					deadMark = reward;
					deadMarkCounter = 0;
				}
			}else {
				deadMemory = reward;
				deadMemoryCounter=0;
			}
		}
		
		
		
	}
	public boolean isInErrorRenge(int i,int j){
		if(i>=j-5 && i<=j+5) return true;
		
		return false;
	}
	public void setDeadPoint(int point){
		deadPoint = point;
		deadMark = 0;
		deadMarkCounter = 0;
		MCAgent.setDeadPoint(deadPoint);
		System.out.println("deadPointは" + deadPoint);
	}
	public void setNextDeadPoint(int point){
		nextDeadPoint = point;
		System.out.println("nextDeadPointは" + nextDeadPoint);
	}
	//////////////////////////////ここからは必要なし//////////////////////////////
	@Override
	public boolean[] getAction() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void integrateObservation(Environment environment) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void giveIntermediateReward(float intermediateReward) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setObservationDetails(int rfWidth, int rfHeight, int egoRow, int egoCol) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void setName(String name) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void giveReward(float reward) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void newEpisode() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setLearningTask(LearningTask learningTask) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public Agent getBestAgent() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public void init() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setEvaluationQuota(long num) {
		// TODO Auto-generated method stub
		
	}
}