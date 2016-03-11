package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;
import java.util.*;

//
//  Viking.java
//
//  Viking is written by John Torgerson and Jesse Halvorsen
//


public class Viking implements LuxAgent
{
    // This agent's ownerCode:
    protected int ID;
    
    // Store some refs the board and to the country array
    protected Board board;
    protected Country[] countries;
    protected int numConts;

    // It might be useful to have a random number generator
    protected Random rand;
    
    // we'll need to calculate attack plans in the placeArmies phase and remember them during the attackPhase
    // so we'll store those plans in this variable
    protected ArrayList<int[]> battlePlan;
    
    // tells moveArmiesIn() to leave some of its armies behind
    // after successfully attacking a country. attackPhase() will calculate this number
    // so that moveArmiesIn() will know how many armies to leave behind
    protected int leaveArmies;
    
    // <borderArmies> will store the number of armies we want to put on a border country
    // <idealBorderArmies> will store the ideal number of armies a border country needs
    // key: country code
    // value: number of armies
    protected Map<Integer, Integer> borderArmies;
    protected Map<Integer, Integer> idealBorderArmies;

    
    // will contain int[] arrays of country codes, called "areas"
    // each area is based on a continent of the map, but may contain
    // extra countries outside of that continent to reduce the number of
    // borders to defend; therefore, some countries will be in
    // more than one area (but no area should have duplicates of any country)
    protected ArrayList<int[]> smartAreas;
    
    // used in findAreaPaths() when we find paths recursively by brute force
    // to keep track of how many we've found so far
    protected int pathCount;
    
    public Viking()
    {
        rand = new Random();
        battlePlan = new ArrayList<int[]>();
        borderArmies = new HashMap<Integer, Integer>();
        idealBorderArmies = new HashMap<Integer, Integer>();
        smartAreas = new ArrayList<int[]>();
    }
    
    // Save references
    public void setPrefs( int newID, Board theboard )
    {
        ID = newID;		// this is how we distinguish what countries we own
        
        board = theboard;
        countries = board.getCountries();
        numConts = board.getNumberOfContinents();
        smartAreas = calculateSmartAreas();
        pathCount = 0;

    }
    
    public String name()
    {
        return "Viking";
    }
    
    public float version()
    {
        return 1.0f;
    }
    
    public String description()
    {
        return "Viking is a bot";
    }
    
    protected void testChat(String topic, String message) {
        String[] topics = {
//            "placeInitialArmies",
            "placeArmies",
            "attackPhase",
//            "moveArmiesIn",
            "fortifyPhase",
            
//            "continentFitness",
//            "getAreaTakeoverPaths",
//            "findAreaPaths",
//            "pickBestTakeoverPaths",
//            "getCheapestRouteToArea",
//            "placeArmiesOnRoutes",
//            "calculateCladeCost",
//            "calculateBorderStrength",
//            "calculateIdealBorderStrength",
//            "getAreaBonuses",
//            "calculateLandgrabObjective",
//            "findWeakestNeighborOwnedByStrongestEnemy",
//            "getSmartBordersArea",
//            "calculateWipeoutObjective",
//            "findContiguousAreas",
            ""
        };
        
        for (int i=0; i<topics.length; i++) {
            if (topic == topics[i]) {
                board.sendChat(message);
                //System.out.println(message);
            }
        }
    }
    
    // pick initial countries at the beginning of the game. We will do this later.
    public int pickCountry() {
        return -1;
    }
    
    // place initial armies at the beginning of the game
    public void placeInitialArmies( int numberOfArmies ) {
        testChat("placeInitialArmies", "*********** PLACE INITIAL ARMIES ***********");
        
//        chatContinentNames();

        // simply call placeArmies(), but we pass 'true' as the second parameter
        // to tell it that we're calling it from placeInitialArmies(), because a few
        // things need to be handled differently when we're placing armies at the
        // beginning of the game
        placeArmies(numberOfArmies, true);
    }
    
    public void cardsPhase( Card[] cards ) {
    }
    
    // place armies at the beginning of each turn
    // we've created an overloaded version of placeArmies() that takes an additional boolean parameter
    // this simply tells us whether it was called by placeInitialArmies() or not;
    // this is useful because there are a few behaviors that need to be different in that case
    public void placeArmies(int numberOfArmies, boolean initial) {
        testChat("placeArmies",
                 "\n**********************************************" +
                 "\n**********************************************" +
                 "\n**************** PLACE ARMIES ****************" +
                 "\n**********************************************" +
                 "\n**********************************************" +
                 "\n Turn: " + board.getTurnCount() + " - Our income: " + board.getPlayerIncome(ID) + " - Total enemy income: " + getTotalEnemyIncome());
        
        // the <initial> boolean is a flag that tells us if this function
        // was called from placeInitialArmies(); we want to clear <battlePlan>
        // every turn, but we don't want to clear it in between placeInitialArmies()
        // calls at the beginning of the game (or before the first turn);
        // <battlePlan> is emptied each turn one path at a time in attackPhase(),
        // so normally it will already be empty here; however, we have to clear it anyway
        // for the special case that placeArmies() was called in the middle of a turn,
        // which happens when we wipeout an enemy and get to cash cards mid-turn;
        // in that case, we want to create a new <battlePlan> from scratch, erasing the old one
        // that attackPhase() is in the middle of, and letting it do the new one instead
        if (initial == false && board.getTurnCount() > 1) {
            battlePlan.clear();
        }
        
        // reset the global <borderArmies> HashMap, which stores the border garrison strength for each border country of each area we want to take over
        // instead of completely clearing it from the previous turn, we want to set each entry to the number of armies
        // that are actually on that country (and if we don't own the country anymore, remove it from the hashmap);
        // the reason for doing this is so we can keep those armies reserved so they aren't used for anything else,
        // even if we don't pick the takeover objective that uses these borders this particular turn;
        // so why set them to the number of extant armies instead of just leaving the value set to the ideal border strength
        // for that country as they were last turn? because we don't want to have to sink more armies into this country
        // if we don't need to (which could otherwise happen if another objective had to interact with this country);
        // if a takeover objective IS picked this turn that uses any of these countries as a border, it will simply overwrite
        // that value in the hashmap with whatever it wants to put there as a border;
        // the values we're setting here only matter if we don't pick that objective this turn;
        if (initial == false && board.getTurnCount() > 1) {
            resetBorderArmies();
        }
        
        // this array list will hold all of the possible objectives we can pursue this turn
        ArrayList<HashMap> objectiveList = new ArrayList<HashMap>();
        
        // find and add list of objectives to knockout enemy bonuses
        objectiveList.addAll(findKnockoutObjectives());
        
        // find and add list of continent takeover objectives
        objectiveList.addAll(findTakeoverObjectives());
        
        // create and add landgrab objective
        objectiveList.add(calculateLandgrabObjective(numberOfArmies));
        
        // find and add wipeout objectives
        ArrayList<HashMap> tempWipeouts = findWipeoutObjectives();
        chatObjectives("placeArmies",tempWipeouts);
        objectiveList.addAll(tempWipeouts);
        
        // sort all the objectives by score
        sortObjectives(objectiveList, "score");
        
        // display a summary of each objective for debugging purposes
        testChat("placeArmies", "--- " + objectiveList.size() + " Possible Objectives: ---");
        for (HashMap objective : objectiveList) {
            String summary = "";
            if (objective != null) {
                summary = (String) objective.get("summary");
            } else {
                summary = "[null objective]";
            }
            testChat("placeArmies", summary);
        }
        testChat("placeArmies", "****** Objectives we're choosing: ******");
    
        // loop through all objectives in order of score
        // picking as many as we can until we're out of armies
        // and placing armies on the appropriate routes as we go
        while (numberOfArmies > 0 && objectiveList.size() > 0) {
            // some stuff for debugging
//            testChat("placeArmies", "~~~~~~~~~~~ LOOP ~~~~~~~~~~~ (Armies Left: " + numberOfArmies + ")");
            int testTemp = numberOfArmies;
            String chatString = "";
            
            // set the <picked> flag to false until we've picked an objective
            boolean picked = false;
            
            // the first objective in the sorted list is the objective with the highest score;
            // we'll pick this objective (except in the special case that it's a knockout objective
            // that costs more than we can afford in a single turn)
            HashMap<String, Object> objective = objectiveList.get(0);
            
            if (objective != null) {
                // store the type of the objective (whether it's a knockout or takeover, etc.)
                String type = "";
                if (objective.containsKey("type")) {
                    type = (String) objective.get("type");
                }
                
                // if the objective is a knockout
                if (type == "knockout") {
                    // pick this objective if we can afford it
                    // (or if we're in the placeInitialArmies() phase, we can pick it even if we can't afford it)
                    if ((Integer) objective.get("cost") <= numberOfArmies || initial == true) {
                        picked = true; // set <picked> flag to true
                        
                        // add the knockout path to battlePlan
                        ArrayList<int[]> objectivePlan = (ArrayList<int[]>) objective.get("plan");
                        battlePlan.addAll(objectivePlan);
                    } else {
                        // if the knockout is too expensive, put it in the back
                        // we will then run the loop again without reassessing the objectives.
                        objectiveList.remove(0);
                        objectiveList.add(objective);
                    }
                }
                // if the objective is a takeover
                else if (type == "takeover") {
                    picked = true;
                    
                    // we need to pick the right countries to place our armies on in order to take over the area
                    // the pickBestTakeoverPaths function will simulate taking over the area
                    // from multiple countries and give us back the best set of paths to do so
                    int[] takeoverArea = (int[]) objective.get("area");
                    ArrayList<int[]> takeoverPlan = pickBestTakeoverPaths(takeoverArea); // get the best paths to take over the goalCont
                    
                    // add the takeover plan paths to <battlePlan>
                    battlePlan.addAll(takeoverPlan);
                    
                    // calculate and store the desired border garrisons on the area we're taking over
                    // so that when we place the armies on battlePlan, it will know how many extra to place for the garrisons
                    setBorderStrength(takeoverArea);
                }
                // if the objective is a landgrab
                else if (type == "landgrab") {
                    picked = true; // set <picked> flag to true
                    
                    // add the knockout path to battlePlan
                    ArrayList<int[]> objectivePlan = (ArrayList<int[]>) objective.get("plan");
                    battlePlan.addAll(objectivePlan);
                }
                // if the objective is a wipeout
                else if (type == "wipeout") {
                    picked = true; // set <picked> flag to true
                    
                    // find the best paths to takeover all of the player's countries
                    int[] wipeoutArea = (int[]) objective.get("area");
                    ArrayList<int[]> wipeoutPlan = pickBestTakeoverPaths(wipeoutArea); // get the best paths to take over all the player's countries
                    
                    // add those paths to battlePlan
                    battlePlan.addAll(wipeoutPlan);
                }
                // if we got here, we don't know what this objective is, so get rid of it
                else {
                    objectiveList.remove(0);
                    testChat("placeArmies","[ERROR: UNKNOWN OBJECTIVE TYPE]");
                }
                
                // if we picked an objective this loop
                // place armies on its routes, then remove it from the list
                // and recalculate all the remaining objectives in case the one we picked affects them in any way
                // and the sort the recalculated list
                if (picked) {
                    // place the number of armies needed to fulfull the objective on the starting countries of all the paths
                    // store any remaining armies available in numberOfArmies
                    numberOfArmies = placeArmiesOnRoutes(battlePlan,numberOfArmies);
                    
                    testTemp = testTemp - numberOfArmies;
                    testChat("placeArmies", (String) objective.get("summary") + ", placed " + testTemp + " armies");
                    
                    // since we've made plans for this objective, we don't need to look at it again
                    // except in the case of the landgrab objective, which we want to keep and recalculate each loop even if we picked it
                    if (type != "landgrab") {
                        objectiveList.remove(0);
                    }
                    
                    // loop through the list of remaining objectives
                    // and recalculate them all
                    for (int i=0; i<objectiveList.size(); i++) {
                        HashMap<String, Object> element = objectiveList.get(i);
                        HashMap<String, Object> newElement = new HashMap<String, Object>(); // instantiate HashMap for recalculated objective, regardless of type
                        
                        // each type uses its own creation function
                        String elementType = "";
                        if (element != null && element.containsKey("type")) {
                            elementType = (String) element.get("type");
                        }
                        if (elementType == "knockout") {
                            newElement = calculateKnockoutObjective((Integer) element.get("continentID"));  // knockouts are generated based off of continent IDs
                        } else if (elementType == "takeover") {
                            newElement = calculateTakeoverObjective((int[]) element.get("area"));  // takeovers are generated based on areas
                        } else if (elementType == "landgrab") {
                            newElement = calculateLandgrabObjective(numberOfArmies); // the landgrab is generated by a number of armies it's allowed to use; in this case, we give it all the remaining armies we have
                        } else if (elementType == "wipeout") {
                            newElement = calculateWipeoutObjective((Integer) element.get("playerID")); // recalculate wipeout objective by passing the player ID
                        }
                        if (newElement != null && !newElement.isEmpty()) { // if the recalculated objective isn't empty or null
                            objectiveList.set(i, newElement); // replace the old one with it
                        } else { // otherwise the element is null (e.g. if the knockout continent is/will be no longer owned by an enemy; i.e. we picked a path through it)
                            objectiveList.remove(i); // remove it from the list
                            i--; // decrement i, because all the elements moved to the left (I know, I know)
                        }
                    }
                    
                    // re-sort the list
                    sortObjectives(objectiveList, "score");
                }
            } else { // this objective doesn't exist
                objectiveList.remove(0); // so remove it and move on to the next one
//                testChat("placeArmies", "[null objective]");
            }
        }
        if (numberOfArmies > 0) {
            testChat("placeArmies","Viking: We've ended placement, but we still have armies......what a schmuck.");
            testChat("placeArmies","        number of armies: " + numberOfArmies + ", objectives left: " + objectiveList.size());
        }
        
//        testChat("placeArmies","--- BATTLE PLAN: ---");
//        chatCountryNames("placeArmies",battlePlan);
    }
    public void placeArmies(int numberOfArmies) {
        placeArmies(numberOfArmies, false);
    }
    
    // attack!
    public void attackPhase() {
        // first some testing/debugging messages
        testChat("attackPhase", "*********** ATTACK PHASE ***********");
        testChat("attackPhase", "Attack Routes:");
        ArrayList<int[]> displayPlan = new ArrayList<int[]>();
        for (int[] route : battlePlan) {
            if (route.length > 1) {
                displayPlan.add(route);
            }
        }
        chatCountryNames("attackPhase",displayPlan);
        
        // loop through battlePlan (calculated in the placeArmies() phase),
        // which contains multiple attack routes, and execute each one
        //for (int i=0; i<battlePlan.size(); i++) {
        while (battlePlan.size() > 0) {
//            testChat("attackPhase", "------- Attack route: -------");
//            chatCountryNames("attackPhase", battlePlan.get(i));
            
            // store the first route in the plan
            int[] attackRoute = battlePlan.get(0);
            
            // remove that route from the plan
            battlePlan.remove(0);
            
            if (countries[attackRoute[0]].getOwner() == ID) { // if we own the first country in the path
                
//                testChat("attackPhase", "First country on route has " + countries[attackRoute[0]].getArmies() + " armies.");
                
                // loop through the whole route, attacking as we go
                for(int j=0; j<attackRoute.length-1; j++) {
                    
//                    testChat("attackPhase", "Calculating forks from this country...");
                    
                    // at each step of the path, before we actually attack
                    // we test for forks. if we find a branch point from this country
                    // then we have to tell moveArmiesIn() to leave some armies behind
                    // in order to take over the fork later from this point
                    int forkArmies = 0; // how many armies we want to leave behind to use for any forks from this country
                    for (int k=0; k<battlePlan.size(); k++) { // loop through only the rest of the battlePlan paths (i.e. the ones we haven't attacked yet) to check for branch points
                        if (attackRoute[j] == battlePlan.get(k)[0]) {
                            forkArmies += calculateCladeCost(battlePlan, k); // calculate cost of any clades that fork from this point, and add them all to forkArmies
                        }
                    }
                    
                    // find out if we want to leave any armies on this country as a border garrison
                    int garrisonArmies = checkBorderStrength(attackRoute[j]);
                    
                    // leaveArmies is a global variable
                    // this is how many armies we want to leave on the attacking country
                    // both for forking from that country and to leave there as a border garrison
                    // moveArmiesIn() will use this variable to do that
                    leaveArmies = forkArmies + garrisonArmies;
                    
                    // now we attack
                    if (countries[attackRoute[j]].getOwner() == ID && countries[attackRoute[j]].getArmies() > 1 && countries[attackRoute[j+1]].getOwner() != ID) { // if we own the attacking country (and have > 1 army in it) and we don't own the defending country
                        board.attack(attackRoute[j],attackRoute[j+1],true); // attack the next country in the route
                        
                        // if we happen to have successfully attacked the last country owned by an enemy here, so that that enemy is now eliminated
                        // often we can cash cards mid-turn; when this happens, Lux will call placeArmies() again and then resume attackPhase() from this point;
                        // placeArmies() will replace <battlePlan> with a new one;
                        // in that case, the j-loop will continue executing our attack through <attackRoute>
                        // but on the next iteration of the while loop, the new <battlePlan> will be executed from the beginning, as desired;
                        // the only source of conflict is that we took over the rest of <attackRoute> in the j-loop before starting on the new <battlePlan>
                        // but if any routes in the new <battlePlan> overlap those countries, it should harmlessly iterate over them
                        // since we're checking for proper ownership of the attacking and defending countries here
                    } else {
                        testChat("attackPhase","Can't attack from " + countries[attackRoute[j]].getName() + " to " + countries[attackRoute[j+1]].getName());
                    }
                }
            }
        }
        
        // now we'll do army 'garbage collection'
        // i.e. find any leftover armies that aren't being used as a border garrison
        // and use them to attack any enemy neighbors they have
        
        // get all the countries we own
        ArrayList<Integer> ourCountries = new ArrayList<Integer>();
        for (Country country : countries) {
            if (country.getOwner() == ID) {
                ourCountries.add(country.getCode());
            }
        }

        // loop through all the countries we own
        for (int country : ourCountries) {
            // only perform garbage collection on countries that are not in the <borderArmies> HashMap at all;
            // that way we leave any extra armies there might be on a border garrison
            // even when placeArmies() thinks they're superfluous this turn; we might want them there later
            if (!borderArmies.containsKey(country)) {
                // the amount of extra armies on this country
                int extraArmies = countries[country].getArmies() - 1;// - checkBorderStrength(country); // (don't need to check the border strength anymore since we're not garbage collecting on countries with border garrisons)
                // if we have any extra armies to work with, we'll attack some enemies until we run out
                if (extraArmies > 0) {
                    
                    testChat("attackPhase", "Performing garbage collection on " + getCountryName(country));
                    
                    int attackingCountry = country;
                    while (extraArmies > 0) { // attack another country on each loop until we run out of armies
                        int defendingCountry = findWeakestNeighborOwnedByStrongestEnemy(attackingCountry); // pick the best enemy neighbor to attack
                        if (defendingCountry == -1) { // if the above function returned -1, it didn't find any enemy neighbors
                            break; // so break the while loop, since we can't attack anyone
                        }
                        leaveArmies = checkBorderStrength(attackingCountry); // <leaveArmies> is a global variable that tells moveArmiesIn() how many armies to leave behind after an attack
                        board.attack(attackingCountry,defendingCountry,true); // attack the country we picked
                        if (countries[defendingCountry].getOwner() == ID) { // if we now own the country, then the attack was successful
                            extraArmies = countries[defendingCountry].getArmies() - 1;// - checkBorderStrength(defendingCountry); // reset <extraArmies> for new country (don't need to check the border strength anymore since we're not garbage collecting on countries with border garrisons)
                            attackingCountry = defendingCountry; // set the country we just conquered as the new attacking country
                        } else { // we ran out of armies before conquering the country
                            extraArmies = 0; // so we have zero armies left, and we're done
                        }
                    }
                }
            }
        }
    }
    
    // decide how many armies to move upon a successful attack
    public int moveArmiesIn( int cca, int ccd)
    {
        // for now, we're always moving everything into the conquered country
        // unless the country we just attacked from is either a branch point for one or more forks
        // or is a border country that we want to leave some armies on as a garrison
        // in which case we will leave behind as many as we have calculated are necessary for both those purposes
        // and move the rest
        
        testChat("moveArmiesIn", "*********** MOVE ARMIES IN ***********");
        
        int armiesOnFrom = countries[cca].getArmies() - 1; // number of armies on the country we just attacked from
        int amountToMove = Math.max(0, armiesOnFrom - leaveArmies); // move number of armies on the country minus leaveArmies
        
        testChat("moveArmiesIn", "Attacking country: " + countries[cca].getName() + "\nArmies on attacking country after attacking (minus one): " + armiesOnFrom + "\nCost of forks/garrison: " + leaveArmies + "\nCountry to move into: " + countries[ccd].getName() + "\nAmount to move: " + amountToMove);
        
        return amountToMove;
    }
    
    public void fortifyPhase() {
        testChat("fortifyPhase", "*********** FORTIFY PHASE ***********");
        
        // first recalculate the border strengths of all the countries in <borderArmies>?
        
        // we will fortify in 2 phases:
        //
        // (1) if any exterior borders are touching each other, do some proportionalization between them
        //
        // (2) we move any unused armies either toward an exterior area border
        //     (but NOT toward any interior borders, i.e. borders that are boxed in by other areas we own)
        //     or toward the nearest country that neighbors an enemy country,
        //     whichever is better, as determined by distance vs. need
        
        // so first find and store the fitness of each exterior border;
        // an exterior border is a border of an area we (fully) own
        // that is not boxed in by other areas we (fully) own;
        // the fitness value stored for each exterior border in the hashmap
        // is its ideal strength / actual strength
        HashMap<Integer,Double> extBordersFitness = findAllExteriorBordersFitness();
        int[] extBorders = convertListToIntArray(extBordersFitness.keySet());
        
        // PHASE 1 - proportionalize between any groups of contiguous borders
        fortifyBetweenExteriorBorders(extBorders);
        
        // PHASE 2 - move any free armies on the board either to an exterior border or to the front
        fortifyFreeArmies(extBordersFitness);

    }
    
    // called when we win the game
    public String youWon()
    { 
        // For variety we store a bunch of answers and pick one at random to return.
        String[] answers = new String[] {
            "I won",
            "beees?!"
        };
        
        return answers[ rand.nextInt(answers.length) ];
    }
    
    public String message( String message, Object data )
    {
        return null;
    }
    
    /*
     *   ********* HELPER / CUSTOM FUNCTIONS *********
     */
    
    
    protected void fortifyBetweenExteriorBorders(int[] extBorders) {
        testChat("fortifyPhase","=== PHASE 1: proportionalize contiguous borders ===");
        
        // find out if any group of exterior borders touch each other
        ArrayList<int[]> clumpedExtBorders = findContiguousAreas(extBorders);
        
        // loop through each clump of borders, and (if any clump is bigger than 1 country)
        // fortify armies between them such that they all have the same percentage of the
        // strength that they are supposed to be (as stored in <borderArmies>)
        for (int[] clump : clumpedExtBorders) {
            if (clump.length > 1) { // if this clump is longer than one, then we have a group of borders that are contiguous with each other
                
                
                // first, find the percentage of desired border strength that we're shooting for
                // given the total number of armies we have to work with in this clump
                int totalClumpArmies = 0; // total number of armies actually on this clump
                int totalDesiredArmies  = 0; // total number of armies in <borderArmies> for every country in this clump
                for (int country : clump) {
                    totalClumpArmies += countries[country].getArmies() - 1;
                    totalDesiredArmies += borderArmies.get(country);
                }
                double plannedPercent = (double) totalClumpArmies / (double) totalDesiredArmies; // the percentage of <borderArmies> value we want to even each country out to
                
                testChat("fortifyPhase","Armies to move for each country in this clump (and borderArmies value):");
                
                // now we'll find and store the difference between the actual number of armies on each country
                // and the number of armies we're shooting for on each country (the calculated percentage of its <borderArmies> value)
                HashMap<Integer, Integer> armyOffset = new HashMap<Integer, Integer>();
                for (int country : clump) {
                    int plannedArmies = (int) Math.floor(plannedPercent * (double) borderArmies.get(country));
                    armyOffset.put(country, countries[country].getArmies() - 1 - plannedArmies);
                    
                    testChat("fortifyPhase",getCountryName(country) + " offset: " + armyOffset.get(country) + " - shooting for: " + plannedArmies + " - (actual armies: " + (countries[country].getArmies()-1) + ", borderArmies: " + borderArmies.get(country) + ")");
                }
                
                // create a list version of the clump that we can loop through
                // and remove countries from as we take care of them
                ArrayList<Integer> moveTo = new ArrayList<Integer>();
                for (int i : clump) {
                    moveTo.add(i);
                }
                
                // so now we have a hashmap containing all the countries in the clump
                // and a measure of how many armies they need to get up to their proper proportion of the available armies;
                // so we'll loop through the clump, picking out the neediest country each iteration
                // and getting it some help from its neighbor borders
                while (moveTo.size() > 0) {
                    // find the neediest country
                    // i.e. the country with the lowest offset that is in <moveTo>
                    int needyCountry = -1;
                    int lowestOffset = moveTo.get(0);
                    for (int country : moveTo) {
                        int offset = armyOffset.get(country);
                        if (offset <= lowestOffset) {
                            lowestOffset = offset;
                            needyCountry = country;
                        }
                    }
                    int offset = armyOffset.get(needyCountry);
                    
                    // if the offset of the neediest country is negative, that means it actually needs some armies
                    // so we'll pull some from its richest neighbor borders
                    if (offset < 0) {
                        
                        testChat("fortifyPhase","-- Neediest country this loop: " + getCountryName(needyCountry) + " (offset: " + offset + ")");
                        
                        // find the countries in the clump that are neighbors of the needy country
                        ArrayList<Integer> neighborBorders = new ArrayList<Integer>();
                        int neighborsTotal = 0;
                        int[] neighbors = countries[needyCountry].getAdjoiningCodeList(); // all neighbors of <needyCountry>
                        for (int neighbor : neighbors) { // loop through neighbors
                            // if this neighbor is one of the exterior borders in this clump
                            // and it has some moveable armies
                            if (armyOffset.containsKey(neighbor) && countries[neighbor].getMoveableArmies() > 0) {
                                neighborBorders.add(neighbor); // add it to the list
                            }
                        }
                        
                        // if <needyCountry> has some neighbor borders that can contribute armies
                        // move some from them to it
                        if (neighborBorders.size() > 0) {
                            
                            // so now we have a list of the neighbors that we can get armies from;
                            // since we want to get countries from our richest neighbors first, we'll
                            // sort the list by each country's offset in descending order
                            for (int i=1; i<neighborBorders.size(); i++) {
                                int sortValue = armyOffset.get(neighborBorders.get(i));
                                int j;
                                for (j=i; j>0 && armyOffset.get(neighborBorders.get(j-1))<sortValue; j--) {
                                }
                                neighborBorders.add(j,neighborBorders.remove(i));
                            }
                            
                            testChat("fortifyPhase","Neighbors of " + getCountryName(needyCountry) + " in descending offset order: " + Arrays.toString(getCountryNames(neighborBorders)));
                            
                            //
                            // now we'll loop over the list, taking armies from the richest country until its offset gets whittled
                            // down to the size of the next richest country's offset, and then equally from both of them
                            // until they get whittled down to the size of the third richest, and so on...
                            //
                            
                            // <offset> is a negative number; <need> is a positive number that represents how many armies we need to try to move to <needyCountry>
                            int need = 0 - offset;
                            
                            // loop through the neighbors, from richest to poorest
                            // as long as we still need some armies
                            for (int i=0; i<neighborBorders.size() && need>0; i++) {
                                
                                // <armiesThisChunk> is the total number of armies we will try to move this iteration,
                                // evenly split from all the countries to the left of and including the ith country
                                int armiesThisChunk;
                                
                                // if we're not on the last neighbor, we want to move just enough armies
                                // from all the neighbors from here to the left so that they match the
                                // offset of the next country in the list; this way, as we go through, we
                                // ensure that we're distributing armies to <needyCountry> evenly from the
                                // richest neighbors, while not taking any from the poorest neighbors
                                // unless we need to
                                if (i < neighborBorders.size() - 1) {
                                    // store the difference between the offsets of the current neighbor and the next one (should always be >= 0)
                                    int difference = armyOffset.get(neighborBorders.get(i)) - armyOffset.get(neighborBorders.get(i+1));
                                    
                                    // so we'll try to move either the difference times the number of neighbors in this chunk (from here to the left)
                                    // or else just the total <need>, whichever is smaller
                                    armiesThisChunk = Math.min(difference * (i+1),need);
                                } else {
                                    // if we're here, we're on the last neighbor in the list,
                                    // so we want to just try to move all the armies we need
                                    // from all of the countries evenly
                                    armiesThisChunk = need;
                                }
                                
                                // this is the number of armies we want to try to move from each neighbor
                                // to the left of and including the ith neighbor
                                int armiesPerCountry = (int) Math.ceil((double) armiesThisChunk / (double) (i+1));
                                
                                // now we'll loop through all the countries to the left of and including the ith neighbor
                                // and try to move the same number of armies from each one (stopping if we've satisfied <need> at any point)
                                // until their offsets are down to the same level as the next country in the list
                                for (int j=0; j<=i && need>0; j++) {
                                    // the country we're moving from this loop
                                    int moveFromCountry = neighborBorders.get(j);
                                    
                                    // the number of armies to actually move is <armiesPerCountry>
                                    // or the max amount of moveable armies this country has,
                                    // or <need>, whichever of the three is smallest
                                    int armiesToMove = Math.min(Math.min(armiesPerCountry,need), countries[moveFromCountry].getMoveableArmies());
                                    
                                    // now actually move the armies from this country to <needyCountry>
                                    if (armiesToMove > 0 && countries[moveFromCountry].getOwner() == ID && countries[needyCountry].getOwner() == ID) { // these are all just safety checks
                                        board.fortifyArmies(armiesToMove, moveFromCountry, needyCountry);
                                        
                                        testChat("fortifyPhase","   ...moving " + armiesToMove + " from " + getCountryName(moveFromCountry) + " (offset: " + armyOffset.get(moveFromCountry) + ") to " + getCountryName(needyCountry) + " (offset: " + armyOffset.get(needyCountry) + ")");
                                    }
                                    
                                    // subtract the armies we moved from <need>
                                    need -= armiesToMove;
                                    
                                    // subtract the armies we moved from the offset for this country
                                    armyOffset.put(moveFromCountry, armyOffset.get(moveFromCountry) - armiesToMove);
                                    
                                    // add the armies we moved to the offset for <needyCountry>
                                    armyOffset.put(needyCountry, armyOffset.get(needyCountry) + armiesToMove);
                                }
                            }
                            

                        } else {
                            // if we're here, <neighborBorders> is empty, which means this country
                            // doesn't have any neighbors that can give it armies, so we'll
                            // remove it from the list and move on to the next neediest country
                            moveTo.remove((Integer) needyCountry); // must cast as an Integer object in order to remove the object that matches <needyCountry> instead of the index
                            
                            testChat("fortifyPhase","Can't move any armies to " + getCountryName(needyCountry) + " - removing from clump");
                        }
                    } else {
                        testChat("fortifyPhase","-- No needy countries in this clump");
                        
                        // if we're here, that means the offset of the neediest country was non-negative,
                        // which means that no country left in the clump actually needs to get any armies from
                        // its neighbors, so we're done
                        break;
                    }
                }
            }
        }
    }
    
    protected void fortifyFreeArmies(HashMap<Integer,Double> extBordersFitness) {
        testChat("fortifyPhase","=== PHASE 2: move free armies toward borders or the front ===");
        
        // create array of exterior borders
        int[] extBorders = convertListToIntArray(extBordersFitness.keySet());
        
        // loop through all the countries we own
        // and move any free armies we find (that aren't on an exterior border)
        // either toward an exterior border or toward the closest country that neighbors an enemy
        testChat("fortifyPhase", "Countries we can move from: ");
        CountryIterator ourCountries = new PlayerIterator(ID, countries);
        while (ourCountries.hasNext()) {
            int country = ourCountries.next().getCode();
            
            // see if this country has any free armies
            int freeArmies = countries[country].getArmies() - 1 - checkIdealBorderStrength(country);
            
            // if this country has free armies and is not itself an exterior border
            // then we want to free move armies from this country to somewhere
            if (freeArmies > 0 && !extBordersFitness.containsKey(country)) {
                
                testChat("fortifyPhase", "== possible paths to fortify from " + countries[country].getName() + " ==");
                
                // there are two possible places we might want to send this country's free armies;
                // the first is one of the exterior border countries, and the second is the nearest
                // country that touches an enemy country; we'll calculate a score for each possible
                // destination out of those possibilities and send the armies to the country
                // with the highest score
                
                double score = 0.0d;
                double highestScore = 0.0d;
                
                // first, we'll find the nearest country that touches an enemy country
                // and score that path appropriately ( 1 / path length^2 )
                int[] pickedPath = pathToNearestCountryWithEnemyNeighbor(country);
                if (pickedPath == null) { // if the function returned null
                    // that means it didn't find any enemies anywhere on the board for some reason;
                    // that should probably never happen, but if it does, we'll set <pickedPath>
                    // to a single-element length array containing just <country>
                    pickedPath = new int[]{country};
                }
                if (!extBordersFitness.containsKey(pickedPath[pickedPath.length-1])) {
                    // score this path, as long as the destination country (last country in the path) isn't an exterior border
                    // (if it is an exterior border, we'll leave the score at the default 0, so it will be ignored,
                    // because in that case we'll already be considering that path when we look at all the exterior borders)
                    highestScore = 1d / Math.pow((double) pickedPath.length, 2); // score is 1 / path length^2
                }
                
                testChat("fortifyPhase",Arrays.toString(getCountryNames(pickedPath)) + " - " + highestScore + " - front lines");
                
                // now loop through all exterior borders and find paths to them,
                // scoring them by ( ideal strength / (actual strength * path length^2) )
                // and pick the highest overall score
                for (int border : extBorders) {
                    // get a path to the next exterior border and calculate its score
                    int[] candidatePath = BoardHelper.friendlyPathBetweenCountries(country, border, countries);
                    if (candidatePath != null) {
                        score = (double) extBordersFitness.get(border) / Math.pow((double) candidatePath.length, 2);
                        
                        testChat("fortifyPhase",Arrays.toString(getCountryNames(candidatePath)) + " - " + score);
                        
                        // if this path has the highest score, pick it
                        if (score > highestScore) {
                            pickedPath = candidatePath;
                            highestScore = score;
                        }
                    }
                }
                
                testChat("fortifyPhase", "Path we're picking: " + Arrays.toString(getCountryNames(pickedPath)));
                
                // fortify the armies along the path with the highest score
                for (int i=0; i<pickedPath.length-1; i++) {
                    int fromCountry = pickedPath[i];
                    int toCountry = pickedPath[i+1];
                    
                    // figure out how many armies to move from this country:
                    // excess armies (above ideal border strength) or moveable armies, whichever is smaller
                    freeArmies = countries[fromCountry].getArmies() - 1 - checkIdealBorderStrength(fromCountry);
                    int moveArmies = Math.min(freeArmies, countries[fromCountry].getMoveableArmies());
                    
                    // do the actual free move,
                    // as long as the amount we can move is greater than 0 and we own both countries
                    if (moveArmies > 0 && countries[fromCountry].getOwner() == ID && countries[toCountry].getOwner() == ID) {
                        board.fortifyArmies(moveArmies, fromCountry, toCountry);
                    } else {
                        // otherwise we can't move anything anymore, so stop looping through the path
                        break;
                    }
                }
            }
        }
    }
    
    // given a country <startCountry>, find and return a path (int[]) to the nearest country
    // that has a neighbor which is not owned by the owner of <startCountry>;
    // if <startCountry> itself has an enemy neighbor, will just return a path of length 1 containing only <startCountry>
    protected int[] pathToNearestCountryWithEnemyNeighbor(int startCountry) {
        // the owner of the starting country
        int owner = countries[startCountry].getOwner();
        
        // we'll store whether we've seen a country before in a boolean array
        // so we don't double count it
        boolean[] alreadySeen = new boolean[countries.length];
        for (int i=0; i<countries.length; i++) {
            alreadySeen[i] = false;
        }
        
        // create a self-sorting stack <Q>, in which we'll store each path we create as we go along;
        // we'll loop around, on each loop picking the shortest path in the stack and finding the neighbors
        // of the last country in it, and adding them each to the end of their own new path,
        // which we'll add to the stack until we find one that's an enemy,
        // in which case we'll return the path leading up to it and we're done
        CountryPathStack Q = new CountryPathStack();
        int country = startCountry;
        int[] path = new int[1];
        path[0] = country;
        while (true) {
            // store this country as seen
            alreadySeen[country] = true;

            // get this country's neighbors and loop through them;
            // we'll test if any of them are an enemy country, and if they are, we're done;
            // if not, we'll add them each to the end of their own new path
            // and add all those paths to the stack
            int[] neighbors = countries[country].getAdjoiningCodeList();
            for (int neighbor : neighbors) {
                if (alreadySeen[neighbor] == false) { // if we haven't already seen this country
                    // if this neighbor is an enemy, then <country> is the last country in the path, so we're done
                    if (countries[neighbor].getOwner() != owner) {
                        return path;
                    }
                    
                    // otherwise, we need to keep searching
                    // so create a new path with this neighbor at the end of it
                    // and push it onto the stack
                    int[] newPath = new int[path.length+1];
                    System.arraycopy(path,0,newPath,0,path.length);
                    newPath[newPath.length-1] = neighbor;
                    Q.pushWithValueAndHistory(countries[neighbor], newPath.length, newPath);
                }
            }
            
            // if the Q is empty, we couldn't find any enemy neighbors at all
            if (Q.isEmpty()) {
                System.out.println("ERROR in pathToNearestCountryWithEnemyNeighbor(): can't find any enemy neighbors");
                return null;
            }
            
            // pop the shortest path and its last country off of the stack for the next loop
            path = Q.topHistory();
            country = Q.pop();
        }
    }

    // returns a hashmap of all borders of all areas (that we fully own) that are not blocked in by another area we own
    // together with their 'fitness', which is simply their ideal strength divided by the number of armies actually on them
    protected HashMap<Integer,Double> findAllExteriorBordersFitness() {
        // the hashmap where we'll store the country codes and fitnesses of all the exterior borders
        HashMap<Integer,Double> extBordersFitness = new HashMap<Integer,Double>();
        
        // get list of all borders
        ArrayList<Integer> candidates = new ArrayList<Integer>(borderArmies.keySet());
        
        // loop through all borders and eliminate borders that are blocked in by an area that we completely own
        Iterator<Integer> iter = candidates.iterator();
        while (iter.hasNext()) {
            int border = iter.next(); // the border country we're checking
            
            // first check if the border itself is part of an area that we fully own;
            boolean validBorder = false;
            int areaIndex = -1;
            for (int i=0; i<smartAreas.size(); i++) { // loop through all areas on the board
                int[] area = smartAreas.get(i);
                if (isInArray(border, area) && playerOwnsArea(area)) { // if the border is in this area and we fully own this area
                    validBorder = true; // then the border is valid
                    areaIndex = i; // save the area that this is part of (we'll need this later to calculate its ideal strength)
                    break; // and we don't need to check the rest of the areas
                }
            }
            
            // if the <validBorder> flag is true, then go ahead and check to see if it's exterior or not
            if (validBorder) {
                int[] neighbors = countries[border].getAdjoiningCodeList(); // get this border's neighbors
                boolean exteriorBorder = false;
                for (int neighbor : neighbors) { // loop through all the neighbors
                    boolean externalNeighbor = true; // flag for whether this neighbor is part of at least one area that we completely own
                    for (int[] area : smartAreas) { // loop through all areas (<smartAreas> is a global list of all the areas on the board)
                        if (isInArray(neighbor, area) && playerOwnsArea(area)) { // if this neighbor is in this area and we fully own this area
                            // if we're here, we know this neighbor is part of at least one area that we completely own
                            externalNeighbor = false; // so set the flag to false
                            break; // and we don't need to loop through the rest of the areas
                        }
                    }
                    // if the <externalNeighbor> flag is true, then this neighbor is not part of any areas that we completely own
                    // so we know that this border is an exterior border and we don't want to remove it
                    if (externalNeighbor) {
                        exteriorBorder = true; // set <exteriorBorder> flag to true
                        break; // we don't need to check the rest of its neighbors
                    }
                }
                // if this is an exterior border
                // then find its fitness (ideal strength / actual armies)
                // and add it to the hashmap
                if (exteriorBorder == true) {
                    
                    int ideal = calculateIdealBorderStrength(border, smartAreas.get(areaIndex));
                    int actual = countries[border].getArmies();
                    double fitness = ((double) ideal + 1d) / (double) actual;

                    // save the border and its fitness in the hashmap
                    extBordersFitness.put(border, fitness);
                }
            }
        }
        
        // now the candidates list should only contain exterior borders
        // so convert it to an int[] array and return it
        return extBordersFitness;
    }
    
    // find the areas with smart borders that we'll use for the whole game;
    // each area is based on a continent of the map, but may contain
    // extra countries outside of that continent to reduce the number of
    // borders to defend; therefore, some countries will be in
    // more than one area (but no area should have duplicates of any country)
    protected ArrayList<int[]> calculateSmartAreas() {
        ArrayList<int[]> areas = new ArrayList<int[]>();
        
        // loop through all the continents to create an "objective" hashmap for each one and add it to objectiveList
        for(int continent = 0; continent < numConts; continent++) {
            // get countries in this continent
            // plus possibly some extra countries outside the continent in order to
            // reduce the number of borders necessary to defend
            int[] area = getSmartBordersArea(getCountriesInContinent(continent));
            
            // add objective to objectiveList arraylist
            areas.add(area);
        }
        return areas;
    }
    
    // returns a list of wipeout objectives, one for each player remaining in the game that isn't us
    // a wipeout objective is a list of attack paths to eliminate a player from the game entirely
    // along with an accompanying score
    protected ArrayList<HashMap> findWipeoutObjectives() {
        // the list of objectives we'll return
        ArrayList<HashMap> objectiveList = new ArrayList<HashMap>();

        int totalPlayers = board.getNumberOfPlayers(); // the number of players that started the game
        
        // loop through all the players
        for (int player=0; player<totalPlayers; player++) {
            // if the player isn't us and the player is still in the game
            if (player != ID && BoardHelper.playerIsStillInTheGame(player, countries)) {
                // then calculate a wipeout objective for this player
                HashMap<String,Object> objective = calculateWipeoutObjective(player);
                
                // if the objective was actually created for this player
                if (objective != null) {
                    // add it to the list
                    objectiveList.add(objective);
                }
            }
        }
        
        // return the list
        return objectiveList;
    }
    
    // calculates a wipeout objective for the given player
    protected HashMap<String, Object> calculateWipeoutObjective(int player) {
        // create the HashMap
        HashMap<String, Object> objective = new HashMap<String, Object>();
        
        // only actually create the objective if the player doesn't have more armies than we'll probably be able to take over in a single turn
        if (BoardHelper.getPlayerArmies(player, countries) < board.getPlayerIncome(ID)) {
        
            // set type
            objective.put("type", "wipeout");
        
            // set player ID
            objective.put("playerID", player);
        
            // set player name
            String playerName = board.getPlayerName(player);
            objective.put("playerName", playerName);
        
            // find and set area
            int[] playerCountries = getPlayerCountries(player); // all countries owned by <player>
            objective.put("area", playerCountries);
            
            // estimate and set cost
            ArrayList<int[]> contiguousAreas = findContiguousAreas(playerCountries); // break up player's countries into contiguous areas to estimate their costs separately
            testChat("calculateWipeoutObjective","Countries owned by " + playerName + ": ");
            chatCountryNames("calculateWipeoutObjective",contiguousAreas);
            Set<Integer> totalCountriesToTake = new HashSet<Integer>(); // will contain every country we mean to take over, including any entry paths we need; we're using a hashset to avoid duplicates
            for(int[] area : contiguousAreas) { // loop through all the contiguous areas
                int[] entryPath = getCheapestRouteToArea(area, false); // find the cheapest path to this area; pass 'false' because we don't care about ending up at the weakest border of the area
                for (int i=1; i<entryPath.length - 1; i++) { // add entryPath to the set, except the first element (which is a country we own, so doesn't count toward cost) and the last element (which is a country in the area, so it will get added separately)
                    totalCountriesToTake.add(entryPath[i]);
                }
                for (int country : area) { // add the countries in the area into the set
                    totalCountriesToTake.add(country);
                }
            }
            int[] totalCountriesToTakeArray = convertListToIntArray(totalCountriesToTake); // convert to array so we can use it in getGlobCost; this array now contains every country owned by <player> and every country we'll need to take over to get to them, with no duplicates
            int cost = getGlobCost(totalCountriesToTakeArray); // the total estimated cost of the objective
            testChat("calculateWipeoutObjective","Countries with entry paths: " + Arrays.toString(getCountryNames(totalCountriesToTakeArray)));
            objective.put("cost", cost);

            // calculate and set score
            float cardsValue = ((float) board.getPlayerCards(player) / 3.0f) * (float) board.getNextCardSetValue();  //(each card is treated as 1/3 the value of the next card set)
            float gain = (float) totalCountriesToTake.size() / 6.0f + cardsValue; // <gain> is the expected increase in our income: mainly the value of the cards we'll get, but also the number of countries we'll take over divided by 3, and then by 2 (an arbitrary reduction to account for the probability that we won't keep these countries)
            float enemyLoss = 0.0f; // <enemyLoss> is how much we reduce the bonus of any enemies we travel through, weighted by their relative income
            for (int country : totalCountriesToTake) { // loop through each enemy country in the (path and) area
                enemyLoss += board.getPlayerIncome(countries[country].getOwner()); // add the income of the owner of each country
            }
            enemyLoss /= 3 * getTotalEnemyIncome() + 0.00001f; // divide the total by 3, because every 3 countries is worth 1 income point, and divide by total enemy income and add a tiny fudge just in case <totalEnemyIncome> is 0
            enemyLoss += cardsValue;
            float score = 10f * ((float) gain + enemyLoss) / ((float) cost + 0.00001f); // the score is our gain + the enemies' loss divided by cost and the square root of the number of turns it will take (to discourage large projects)
            objective.put("score", score);
            
            // create and set summary string (this is just useful for debugging)
            String summary = "wipeout   - score: ";
            String scoreStr = "" + score;
            summary += scoreStr.length() >= 6 ? scoreStr.substring(0, 6) : scoreStr;
            summary += " - " + playerName + ", cost: " + cost;
            objective.put("summary", summary);
        
            return objective;
        } else {
            return null;
        }
    }
    
    // the idea of a Landgrab objective is just to take over a bunch of countries,
    // not to take over a continent, just to increase the number of our countries
    // and decrease that of our enemies; this function takes a number of armies
    // and finds a path from one of our countries that would take over as many
    // countries as possible with those armies, balanced by doing as much damage as
    // possible to the strongest enemies (so it might take a shorter path if that one
    // does more damage to a very strong enemy than a longer one)
    protected HashMap<String, Object> calculateLandgrabObjective(int armies) {
        HashMap<String, Object> objective = new HashMap<String, Object>();
        int[] ourCountries = getPlayerCountries(); // get list of countries we own
        
        testChat("calculateLandgrabObjective", "----- Calculate Landgrab Objective -----");
        
        // recursively find weakest (preferring those owned by strongest opponent) enemy neighbors from each country we own to make several paths
        ArrayList<ArrayList <Integer>> candidatePaths = new ArrayList<ArrayList <Integer>>();
        for (int ourCountry : ourCountries) {
//            testChat("calculateLandgrabObjective", "--- Picking neighbor of " + getCountryName(ourCountry) + ":");
            
            ArrayList<Integer> path = new ArrayList<Integer>(); // will contain the current path
            path.add(ourCountry); // initially add the start country to the path
            float armiesLeft = (float) armies;
            while (armiesLeft >= 0) { // keep finding countries for the path as long as we have at least 1 army
                int nextCountry = findWeakestNeighborOwnedByStrongestEnemy(path.get(path.size()-1), path); // find the next country in the path
                if (nextCountry != -1) { // if the function returned an actual enemy neighbor
                    path.add(nextCountry); // add it to the path
                    armiesLeft -= (float) countries[nextCountry].getArmies() * 0.5f + 1f; // subtract the cost of taking over that neighbor from <armiesLeft>
                } else { // otherwise there were no enemy neighbors,
                    break; //  so we're done with this path, even if we have armies left
                }
            }
            // if the path is longer than 1, add the path to the list of candidates
            // if it's only 1 element long, the starting country didn't have any enemy neighbors at all, so we want to ignore it
            if (path.size() > 1) {
                candidatePaths.add(path);
            }
        }
        
        testChat("calculateLandgrabObjective", "Candidate paths:");
        
        //
        // pick the best path
        // we loop through all the paths and calculate their score (our gain + enemy losses divided by actual cost)
        // and pick the path with the highest score
        //
        ArrayList<Integer> pickedPath = new ArrayList<Integer>(); // will contain the path we pick
        float highestScore = 0.0f; // the highest value (gain + enemy losses over cost) we've seen so far, as we loop through and check each path
        int pickedPathCost = 0; // the cost of the path we'll pick
        // we want to prefer paths which run through less populated areas;
        // we'll do a rough approximation of that by calculating the enemy army density in each continent
        // and when we're choosing between candidate paths, we'll prefer paths in countries with fewer enemy armies per country;
        // so first we'll calculate the free army density of each continent (how many enemy armies above 1 there are per country)
        float[]continentDensity = new float[numConts]; // this array will hold the densities of all the continents on the board
        testChat("calculateLandgrabObjective", "===Continent Army Densities===");
        for (int cont=0; cont<numConts; cont++) { // loop through all the continents
            int[] enemyCountries = getEnemyCountriesInContinent(ID, cont); // all the enemy countries in this continent
            int freeArmies = 0;
            for (int enemyCountry : enemyCountries) { // loop through the enemy countries
                freeArmies += countries[enemyCountry].getArmies() - 1; // add up all the free armies
            }
            int numCountries = BoardHelper.getContinentSize(cont, countries); // the number of countries in this continent
            float freeArmyDensity = (float) freeArmies / (float) numCountries; // this continent's density is the number of free enemy armies / the total number of countries
            continentDensity[cont] = freeArmyDensity; // store the density in the array of all continents' densities
            testChat("calculateLandgrabObjective", board.getContinentName(cont) + ": " + freeArmyDensity);
        }
        // next, we'll adjust that density by adding the average density of all its neighboring continents divided by 2
        float[] adjustedContDensity = new float[numConts]; // this array will hold the adjusted densities for all the continents
        float highestAdjustedDensity = 0.0f; // will contain the highest adjusted density of all continents
        for (int cont=0; cont<numConts; cont++) { // loop through the continents again
            int[] neighbors = getNeighboringContinents(cont); // an array of continents that have countries that can attack the continent
            float adjustedDensity = continentDensity[cont]; // start by assigning the continent's density to <adjustedDensity>
            for (int neighbor : neighbors) {
                adjustedDensity += continentDensity[neighbor] / (neighbors.length * 2); // then add the average densities of all the neighbor continents divided by 2
            }
            adjustedContDensity[cont] = adjustedDensity; // put this continent's adjusted density into the array
            if (adjustedDensity > highestAdjustedDensity) { // if this is the highest adjusted density we've seen so far
                highestAdjustedDensity = adjustedDensity; // save it in <highestAdjustedDensity>
            }
            testChat("calculateLandgrabObjective", "Neighbors of " + board.getContinentName(cont) + ": " + Arrays.toString(getContinentNames(neighbors)));
            testChat("calculateLandgrabObjective", "Adjusted density of " + board.getContinentName(cont) + ": " + adjustedDensity);
        }
        testChat("calculateLandgrabObjective", "highest adjusted density: " + highestAdjustedDensity);
        // now we'll calculate a score for each path, and pick the one with the best score
        // the score accounts for:
        //   (1) the number of countries we gain by taking it over
        //   (2) the number of countries our enemies will lose, adjusted so that we prefer to take countries from stronger enemies
        //   (3) the cost of taking over the path
        //   (4) the number of free enemy armies in nearby continents
        for (ArrayList<Integer> path : candidatePaths) { // loop through all candidate paths
            // first, calculate our gain and the enemy losses from taking over the path
            int length = path.size(); // the length of the path
            float gain = 0.0f; // the value of the countries we gain
            float enemyLoss = 0.0f; // the loss to our enemies when we take over the countries in this path
            for (int i=1; i<length; i++) { // loop through all the countries in this path except the first one (which we own)
                gain += (1.0f - adjustedContDensity[countries[i].getContinent()] / highestAdjustedDensity) / 2; // this is the calculated value of each country designed to favor continents with fewer enemy armies around (value should be between 0.0 and 0.5)
                enemyLoss += board.getPlayerIncome(countries[path.get(i)].getOwner()); // add the income of the owner of each country
            }
            gain /= 3.0f; // divide the total gain by 3, because every 3 countries is worth 1 income point
            enemyLoss /= 3 * getTotalEnemyIncome() + 0.00001f; // divide the total by 3, because every 3 countries is worth 1 income point, and divide by total enemy income (and add a tiny fudge just in case <totalEnemyIncome> is 0)
            
            // then calculate the actual cost of taking over the path
            int cost = getPathCost(convertListToIntArray(path));
            
            // and the score
            float score = 10f * (gain + enemyLoss) / ((float) cost + 0.00001f);
            
            // then compare this path's score to the highest we've seen so far
            // and if they are higher, tentatively choose this path (update <pickedPath>, <pickedPathCost> and <highestScore> to this path)
            if (score > highestScore) {
                highestScore = score;
                pickedPath = path;
                pickedPathCost = cost;
            }
            
            chatCountryNames("calculateLandgrabObjective", path);
            testChat("calculateLandgrabObjective", "score: " + score);
        }
        
        testChat("calculateLandgrabObjective", "--- The path we're picking: --- (score: " + highestScore + ")");
        chatCountryNames("calculateLandgrabObjective", pickedPath);
        
        // now we've picked the best path,
        // so we'll package it into an objective HashMap;
        // if there was no path with a score greater than 0 (or no path at all)
        // then we'll just return null;
        if (pickedPath.size() > 0) { // if we picked a path
            // set type
            objective.put("type","landgrab");
            
            // set plan
            int[] route = convertListToIntArray(pickedPath); // convert the path we picked into an int array
            ArrayList<int[]> plan = new ArrayList<int[]>();
            plan.add(route); // package the path into an array list
            objective.put("plan",plan); // add to objective
            
            // set cost
            objective.put("cost", pickedPathCost);
            
            // set score
            objective.put("score", highestScore);
            
            // set summary string (this is just useful info for debugging)
            String summary = "landgrab  - score: ";
            String scoreStr = "" + highestScore;
            summary += scoreStr.length() >= 6 ? scoreStr.substring(0, 6) : scoreStr;
            summary += " - " + getCountryName(route[0]) + "..." + getCountryName(route[route.length - 1]) + ", ";
            summary += "cost: " + pickedPathCost;
            objective.put("summary", summary);
            
            return objective;
        } else {
            return null;
        }
    }
    
    // findTakeoverObjectives() creates an "objective" hashmap for each continent on the board
    // and packages all the objectives in an array list, which it returns to the placeArmies() function
    // which will use the information to decide which ones to take over each turn, if any
    //
    // each takeover objective corresponds to a continent. it packages all the countries in that continent,
    // plus possibly a few external countries (in order to consolidate the borders it needs to defend so there will be fewer of them)
    // into an integer array of country codes called an "area", and stores that array as a value.
    // it also stores an estimate of the cost of taking over that area,
    // along with the continent bonus and the number of borders.
    //
    // this information should be enough for placeArmies() to prioritize takeover objectives
    // each turn based on the number of armies available and balanced against other types of objectives (such as knockout objectives)
    protected ArrayList<HashMap> findTakeoverObjectives() {
        ArrayList<HashMap> objectiveList = new ArrayList<HashMap>();
        
        // loop through all the areas to create an "objective" hashmap for each one and add it to objectiveList
        for (int[] area : smartAreas) {
            
            // create takeover objective of this area
            HashMap<String, Object> objective = calculateTakeoverObjective(area);
            
            // FORCE THE BOT TO ALWAYS CHOOSE TO TAKEOVER A PARTICULAR CONTINENT FOR TESTING PURPOSES
/*            int[] continents = (int[]) objective.get("continentIDs");
            for (int continent : continents) {
                String name = board.getContinentName(continent);
                if (name.equals("Canada")) {
                    objective.put("score", Float.MAX_VALUE);
                    board.sendChat("SETTING SCORE FOR " + name + " TO MAX FLOAT VALUE");
                }
            }
*/
            
            // add objective to objectiveList arraylist
            objectiveList.add(objective);
        }
        
        return objectiveList;
    }
    
    protected HashMap<String, Object> calculateTakeoverObjective(int[] area) {
        HashMap<String, Object> objective = new HashMap<String, Object>(); // the Objective hashmap for this continent
        
        // set objective type
        objective.put("type", "takeover");
        
        // set continent code
        int[] continents = getAreaContinentIDs(area);
        objective.put("continentIDs", continents);
        
        // set area
        objective.put("area", area);
        
        // set continent bonus
        int bonus = getAreaBonuses(area);
        objective.put("bonus", bonus);
        
        // find and set number of borders
        int numBorders = 0;
        for (int country : area) {
            if (isAreaBorder(country, area)) {
                numBorders += 1;
            }
        }
        objective.put("numBorders", numBorders);
        
        // find and set cost
        //
        // this is an estimate of how many armies we'll need to take over the area,
        // including erecting border garrisons on all the borders.
        // since it's expensive to find the actual attack paths, we're not doing that here.
        // instead, we're simply treating all the enemy countries in the area (plus a path to the area if needed)
        // as one big glob, and estimating the cost as if the glob were a single long path.
        // when it comes time for the bot to actually place armies to take over the area,
        // it will have calculated the attack paths it needs to do so, and figured out exactly how many armies
        // it needs to succesfully prosecute those paths. that number will be more precise than this estimate,
        // but this should be close.
        int cost;
        int[] enemyCountries = getEnemyCountriesInArea(area); // get enemy countries in the area
        int[] entryPath = getCheapestRouteToArea(area, false); // cheapest path to area (in case we don't own any countries); pass 'false' because we don't care about ending up at the weakest border of the area
        ArrayList<Integer> pathAndAreaList = new ArrayList<Integer>(); // new arraylist to hold the path and the countries in the area; we'll have to use an arraylist for the moment because it's easier than figuring out how big it has to be before we populate it
        for (int i=1; i<entryPath.length - 1; i++) { // add entryPath to new arraylist, except the first element (which is a country we own, so doesn't count toward cost) and the last element (which is a country in the area, so it's already in enemyCountries)
            pathAndAreaList.add(entryPath[i]);
        }
        for (int country : enemyCountries) { // add the enemy countries in the area into the new arraylist
            pathAndAreaList.add(country);
        }
        int[] pathAndArea = convertListToIntArray(pathAndAreaList); // convert the arraylist into an array for use in getGlobCost(); pathAndArea now holds all the enemy countries in the area we're taking over, plus any enemy countries we have to take over to get there
        cost = getGlobCost(pathAndArea); // estimate the cost of the area and the path together
        for (int country : area) { // now loop through every country in the area to add border garrisons to the cost
            int borderStrength = calculateBorderStrength(country, area, calculateIdealBorderStrength(country,area), bonus); // what the border garrison should be for this country; if it is not a border, this will be 0
            int extantBorderArmies = getProjectedCountryOwner(country) == ID ? getProjectedArmies(country) - 1 : 0; // how many (extra) armies are on this country if we own it; if we don't own it, 0
            cost += Math.max(0,borderStrength - extantBorderArmies); // add the border strength we want to the cost minus any armies we already have on that border (or 0 if there are more than enough armies already there)
        }
        objective.put("cost", cost);
        
        // old cost with no borders (for testing)
        float oldCost = 0;
        for (int country : pathAndArea) {
            oldCost += countries[country].getArmies();
        }
        oldCost *= 1.1f;
        oldCost += pathAndArea.length;
        int oldCostInt = (int) Math.ceil(oldCost);
        objective.put("oldCost",oldCostInt);
        
        // calculate and set score (legacy)
        float turns = Math.max(1, (float) cost / ((float) board.getPlayerIncome(ID) + .00001f));
        float score = 10f * (float) bonus / (((float) cost + 0.00001f) * (float) Math.pow(turns, .5));
        objective.put("oldScore", score);
        
        // calculate and set score
        float gain = bonus + (float) area.length / 3.0f + (float) Math.max(0,entryPath.length-2) / 6.0f; // <gain> is the expected increase in our income: the area bonus + the number of countries divided by 3 + any countries we'll take over on the way there divided by 3, and then by 2 (an arbitrary reduction to account for the probability that we won't keep these countries)
        float enemyLoss = 0.0f; // <enemyLoss> is how much we reduce the bonus of any enemies we travel through, weighted by their relative income
        for (int country : pathAndArea) { // loop through each enemy country in the (path and) area
            enemyLoss += board.getPlayerIncome(countries[country].getOwner()); // add the income of the owner of each country
        }
        enemyLoss /= 3 * getTotalEnemyIncome() + 0.00001f; // divide the total by 3, because every 3 countries is worth 1 income point, and divide by total enemy income and add a tiny fudge just in case <totalEnemyIncome> is 0
        score = 10f * ((float) gain + enemyLoss) / (((float) cost + 0.00001f) * (float) Math.pow(turns, .5)); // the score is our gain + the enemies' loss divided by cost and the square root of the number of turns it will take (to discourage large projects)
        objective.put("score", score);
        
        // set summary string (this is just useful info for debugging)
        String summary = "takeover  - score: ";
        String scoreStr = "" + score;
        summary += scoreStr.length() >= 6 ? scoreStr.substring(0, 6) : scoreStr;
        summary += " - " + Arrays.toString(getContinentNames(continents)) + ", ";
        summary += "bonus: " + bonus + ", cost: " + cost;// + ", oldCost: " + oldCostInt;
        objective.put("summary", summary);
        
        return objective;
    }
    
    // sort in place an arraylist of objectives
    // by the value of sortKey (only if that value is an integer)
    protected void sortObjectives(ArrayList<HashMap> list, String sortKey) {
        // if there's nothing in the list
        // simply return the original list
        if (list.size() == 0) {
            return;
        }
        
        // if the values at sortKey are integers
        HashMap<String,Object> someElement = list.get(0); // we'll check the first element to find out what type sortKey is
        if (someElement != null && someElement.get(sortKey) instanceof Integer) {
            // bubble-sort the arraylist by the value of sortKey
            boolean flag = true;
            HashMap temp = new HashMap();
            int v1, v2;
            HashMap<String, Object> thisObj = new HashMap<String, Object>();
            HashMap<String, Object> nextObj = new HashMap<String, Object>();
            int size = list.size();
            while(flag) {
                flag = false;
                for (int i=0; i<size-1; i++) {
                    thisObj = list.get(i); // the element we're on
                    nextObj = list.get(i+1); // the next element, to compare it to
                    
                    if (thisObj != null && thisObj.containsKey(sortKey)) { // if this element has the sortKey
                        v1 = (Integer) thisObj.get(sortKey); // assign the sortKey value to v1
                    } else { // and if it doesn't
                        v1 = Integer.MIN_VALUE; // assign v1 the lowest possible value, so this element will be moved to the end
                    }
                    if (nextObj != null && nextObj.containsKey(sortKey)) { // if the next element has the sortKey
                        v2 = (Integer) nextObj.get(sortKey); // assign the sortKey value to v2
                    } else { // and if it doesn't
                        v2 = Integer.MIN_VALUE; // assign v2 the lowest possible value
                    }
                    
                    if (v1 < v2) {
                        temp = list.get(i); // store the value at i
                        list.remove(i); // remove the ith element, and everything after it shifts to the left
                        list.add(i+1,temp); // insert the original ith element at i+1, and everything after it shifts to the right
                        flag = true;
                    }
                }
            }
        }
        // if the values at sortKey are floats
        else if (someElement != null && someElement.get(sortKey) instanceof Float) {
            // bubble-sort the arraylist by the value of sortKey
            boolean flag = true;
            HashMap temp = new HashMap();
            float v1, v2;
            HashMap<String, Object> thisObj = new HashMap<String, Object>();
            HashMap<String, Object> nextObj = new HashMap<String, Object>();
            int size = list.size();
            while(flag) {
                flag = false;
                for (int i=0; i<size-1; i++) {
                    thisObj = list.get(i); // the element we're on
                    nextObj = list.get(i+1); // the next element, to compare it to
                    
                    if (thisObj != null && thisObj.containsKey(sortKey)) { // if this element has the sortKey
                        v1 = (Float) thisObj.get(sortKey); // assign the sortKey value to v1
                    } else { // and if it doesn't
                        v1 = -Float.MAX_VALUE; // assign v1 the lowest possible value, so this element will be moved to the end
                    }
                    if (nextObj != null && nextObj.containsKey(sortKey)) { // if the next element has the sortKey
                        v2 = (Float) nextObj.get(sortKey); // assign the sortKey value to v2
                    } else { // and if it doesn't
                        v2 = -Float.MAX_VALUE; // assign v2 the lowest possible value
                    }
                    
                    if (v1 < v2) {
                        temp = list.get(i); // store the value at i
                        list.remove(i); // remove the ith element, and everything after it shifts to the left
                        list.add(i+1,temp); // insert the original ith element at i+1, and everything after it shifts to the right
                        flag = true;
                    }
                }
            }
        }

        return;
    }
    
    // finds all continents that are completely owned and returns an arraylist containing an Objective (hashmap) for each of them
    // Objective hashmaps for knocking out bonuses contain the cost to knock out the continent bonus, the continent bonus, the enemy's current income, an attack path to get to the continent, and where to place armies to execute the attack path
    protected ArrayList<HashMap> findKnockoutObjectives() {
        ArrayList<HashMap> objectiveList = new ArrayList<HashMap>();
        
        // loop through all the continents on the board
        // if the continent is fully owned, find all the values we want and put them in a hashmap
        // add the hashmap to objectiveList
        for(int continent=0; continent<numConts; continent++) { // loop through all the continents
            HashMap<String, Object> objective = new HashMap<String, Object>(); // the Objective hashmap for this continent
            objective = calculateKnockoutObjective(continent);
            if (objective != null) {
                objectiveList.add(objective);
            }
        
        }
        return objectiveList;
    }

    // creates/calculates knockout objective for the given continent
    // if the continent is not fully owned by an enemy, returns null
    protected HashMap<String, Object> calculateKnockoutObjective(int continent) {
        int[] area = getCountriesInContinent(continent);
        int owner = countries[BoardHelper.getCountryInContinent(continent, countries)].getOwner(); // the owner of some country in this continent
        if (BoardHelper.anyPlayerOwnsContinent(continent, countries) && owner != ID && !battlePlanHasCountryIn(area)) { // if an enemy fully owns this continent
            HashMap<String, Object> objective = new HashMap<String, Object>(); // the Objective hashmap for this continent
            
            // set objective type
            objective.put("type", "knockout");
            
            // set continent code
            objective.put("continentID", continent);
            
            // set continent bonus
            int bonus = board.getContinentBonus(continent);
            objective.put("bonus", bonus);
            
            // set enemy income
            int enemyIncome = board.getPlayerIncome(owner);
            objective.put("enemyIncome", enemyIncome);
            
            // find and set route
            //
            // first, actually find the cheapest route to the continent
            // we pass 'true' to the function to tell it to account for the number of armies
            // on the border country we end up at, because we want to find the weakest way into the continent
            int[] route = getCheapestRouteToCont(continent, true);
            ArrayList<int[]> plan = new ArrayList<int[]>();
            plan.add(route); // package the path into an array list
            objective.put("plan",plan);
            
            // find and set cost
            int cost = getPathCost(route);
            objective.put("cost", cost);
            
            // calculate and set score
            int totalEnemyIncome = getTotalEnemyIncome();
            int income = board.getPlayerIncome(ID); // our income
            float score = 10f * ((float) bonus * enemyIncome) / ( (cost + 0.00001f) * (totalEnemyIncome + 0.00001f));
            objective.put("oldScore", score);
            
            // calculate and set score
            float countriesGain = 0.0f; // countriesGain is how much we reduce the bonus of any enemies we travel through, weighted by their relative income
            for (int i=1; i<route.length; i++) { // loop through each country in the route, except for the first one, which we own
                countriesGain += board.getPlayerIncome(countries[route[i]].getOwner()); // add the income of the owner of each country
            }
            countriesGain /= 3 * totalEnemyIncome + 0.00001f; // divide the total by 3, because every 3 countries is worth 1 income point, and divide by total enemy income
            float continentGain = ((float) bonus * enemyIncome) / (totalEnemyIncome + 0.00001f); // continentGain is how much we reduce the bonus of the enemy that owns the continent by taking away the continent, weighted by its relative income
            score = 10f * (countriesGain + continentGain) / (cost + 0.00001f); // score is the total gain divided by the cost
            objective.put("score", score);
            
            // set summary string (just some useful info for debugging)
            String summary = "knockout - score: ";
            String scoreStr = "" + score;
            summary += scoreStr.length() >= 6 ? scoreStr.substring(0, 6) : scoreStr;
            summary += " - " + board.getContinentName(continent);
            summary += ", bonus: " + bonus + ", cost: " + cost;
            objective.put("summary", summary);
            
            return objective;
        } else {
            return null;
        }
    }
    
    // operates on the global HashMap <borderArmies>, which contains the border garrison strength calculated
    // for all the border countries of all the areas we've decided to take over;
    // this function resets those values to equal the number of armies actually on each country in the hashmap
    // (or removes the country from the hashmap if we no longer own it);
    // we do this at the beginning of each turn; see placeArmies() where the function is called for why;
    // also removes any countries from <idealBorderArmies> that we no longer own, but does not adjust the values
    // of the ones we still do own
    protected void resetBorderArmies() {
        Iterator iter = borderArmies.keySet().iterator();
        while (iter.hasNext()) { // iterate through the hashmap
            int key = (Integer) iter.next();
            Country country = countries[key];
            if (country.getOwner() == ID) { // if we own this country
                borderArmies.put(key, country.getArmies() - 1); // set the value to the number of armies on that country (minus 1)
            } else { // otherwise we don't own this country
                iter.remove(); // so remove this entry from the hashmap altogether
            }
        }
        Iterator iter1 = idealBorderArmies.keySet().iterator();
        while (iter1.hasNext()) { // iterate through the hashmap
            int key = (Integer) iter1.next();
            Country country = countries[key];
            if (country.getOwner() != ID) { // if we don't own this country
                iter1.remove(); // remove this entry from the hashmap altogether
            }
        }
    }
    
    // checks if country is in borderArmies hashmap, and if it is, returns the value, if not, returns 0
    // i.e. if the country is a border, this function will return the number of armies we intend to put/leave on it as a garrison
    protected int checkBorderStrength(int country) {
        if (borderArmies.get(country) != null) {
            return borderArmies.get(country);
        }
        return 0;
    }
    
    // checks if country is in idealBorderArmies hashmap, and if it is, returns the value, if not, returns 0
    // i.e. if the country is a border, this function will return the number of armies that would make up a garrison of ideal strength
    protected int checkIdealBorderStrength(int country) {
        if (idealBorderArmies.get(country) != null) {
            return idealBorderArmies.get(country);
        }
        return 0;
    }

    // figures out how many armies to put on each border country of the given area
    // stores the number of armies it calculates for each country in the global hashmap borderArmies
    // does NOT actually place those armies on the countries
    protected void setBorderStrength(int[] area) {
        int[] borders = getAreaBorders(area);
        int numBorders = borders.length; // the number of borders <area> has
        int areaBonus = getAreaBonuses(area); // any continent bonuses contained within <area>
        for (int country : borders) {
            int idealStrength = calculateIdealBorderStrength(country, area);
            int strength = calculateBorderStrength(country, area, idealStrength, areaBonus);
            borderArmies.put(country, strength); // borderArmies is a global hashmap
            idealBorderArmies.put(country, idealStrength); // idealBorderArmies is a global hashmap
        }
    }
    
    // calculate how many armies to leave on the given country as a border garrison
    // if the country is not a border, will return 0;
    protected int calculateBorderStrength(int country, int[] area, int idealStrength, int areaBonus) {
        int strength = 0;
        if (isAreaBorder(country, area)) { // if <country> is a border of <area>
            
            // find the relative value of this area compared to the highest continent bonus on this map
            // which we'll use to tailor the garrison strength for this country to the area's "importance" as it were
            int biggestBonus = getBiggestContinentBonus();
            double areaValue = Math.pow(areaBonus, 0.5)/Math.pow(biggestBonus, 0.5); // we soften the ratio a bit by using square roots
            
            // find any armies we may already have on this country
            // and decide the maximum number of armies we want to add to that each turn (as a portion of our income)
            // (up to the ideal value, which we'll calculate later)
            double income = (double) board.getPlayerIncome(ID);
            double incomePortion = income / 4.0d; // our income divided by 4
            int extantArmies = 0; // the number of (our) armies on this country, if any
            if (countries[country].getOwner() == ID) { // if we (actually) own the country
                extantArmies = countries[country].getArmies(); // get the extant armies
            }
            // here we cheat a little, and just make sure that <extantArmies> is never reported as less than <incomePortion> (rounded up);
            // that just gives us a boost when we're first putting garrisons on this country,
            // by allowing us to put double <incomePortion> the first time around
            extantArmies = (int) Math.ceil(Math.max(extantArmies, incomePortion));
            
            // so now we're ready to calculate the strength;
            // sets <strength> to the ideal strength, scaled down by the relative value of the bonus we're protecting
            // for a small bonus it will have an insufficient border,
            // and the largest bonus on the board will have a bonus equal to <idealStrength>
            // except if <extantArmies> + <incomePortion> is smaller than that number, in which case we limit <strength> to that
            // so we never have to add more than that much of our income at once
            // in order to keep the border garrison requirements from being out of control
            // but the garrison will be allowed to grow each turn until it reaches the ideal value
            strength = (int) Math.ceil(Math.min(idealStrength * areaValue,  (double) income / 2));//extantArmies + incomePortion)); <-- commenting out the incremental limit for now because it doesn't work very well; we'll come back to it
            
            testChat("calculateBorderStrength", "Border strength of " + countries[country].getName() + " is " + strength);
        }
        
        return strength;
    }
    // overloaded version without the <bonuses> parameter
    protected int calculateBorderStrength(int country, int[] area, int idealStrength) {
        int areaBonus = getAreaBonuses(area); // set this to the bonus of all continents completely contained by <area>
        return calculateBorderStrength(country, area, idealStrength, areaBonus);
    }
    
    // called by calculateBorderStrength()
    // returns the ideal strength for this border, which is
    // the magnitude of the greatest nearby enemy threat to the given border country
    // plus a certain amount of padding
    protected int calculateIdealBorderStrength(int borderCountry, int[] area) {
        int maxDepth = 5; // the depth we want to search out to
        int currentDepth = 0; // begin with a depth of 0
        int armiesThusFar = 0; // the armies on a given path so far
        ArrayList<Integer> blacklist = new ArrayList<Integer>(); // the blacklist keeps track of the players whose countries we've seen so far along a given path
        blacklist.add(ID); // begin by adding ourselves, so we don't ever consider ourselves a threat
        
        // recursively find the greatest threat from all neighbors of <borderCountry> out to <currentDepth>
        int greatestThreat = findNeighborsThreat(borderCountry, area, currentDepth, maxDepth, armiesThusFar, blacklist);
        
        // multiply the greatest threat by 1.2, because this should give us decent odds of repelling an attack
        int idealStrength = (int) Math.round(greatestThreat * 1.2d);
        
        return idealStrength;
    }
    
    // called by calculateIdealBorderStrength()
    // recursively finds neighbors of given country, and finds the threat level of those neighbors,
    // then compares all the threats it finds to each other and returns the highest one
    protected int findNeighborsThreat(int country, int[] area, int currentDepth, int maxDepth, int armiesThusFar, ArrayList<Integer> oldBlacklist) {
        // make deep copy of oldBlacklist, so we don't mess with other branches
        ArrayList<Integer> blacklist = new ArrayList<Integer>();
        blacklist.addAll(oldBlacklist);
        
        int threat = 0; // the threat from this country
        int owner = getProjectedCountryOwner(country); // the owner of <country>
        int armies = getProjectedArmies(country); // the armies on <country>
        
        // if we're not on the first country (the actual border country)
        // calculate the threat
        if (currentDepth > 0) {
            // if <owner> is not in blacklist, then we haven't run across a country owned by this player before
            // so we assess the threat of this country
            // (if we HAVE seen this owner before, we don't assess the threat, because the player won't
            // be able to get to the area along this path, because it will run into itself, in which case
            // we will leave threat at 0, as it was initially assigned)
            if (!isInArray(owner, blacklist)) {
                threat = Math.max(0,armies + getPlayerIncomeAndCards(owner) - armiesThusFar - currentDepth);
                blacklist.add(owner); // now we've seen this owner on this path, so add it to blacklist
            }
            
            // add the armies on this country to the total armies we've seen so far along this path
            // to pass to the new neighbors
            armiesThusFar += armies;
        }
        
        // iterate <currentDepth> to pass to the new neighbors
        currentDepth++;
        
        // if we haven't exceeded <maxDepth> yet, go on to check neighbors
        if (currentDepth <= maxDepth) {
            // find neighbors of <country>
            int[] neighbors = BoardHelper.getAttackList(countries[country], countries);
            
            // loop over the neighbors of <country> and recurse on them
            for(int neighbor : neighbors) {
                // only recurse on neighbors that aren't in the area
                if (!isInArray(neighbor, area)) {
                    int neighborThreat = findNeighborsThreat(neighbor, area, currentDepth, maxDepth, armiesThusFar, blacklist);
                    if (neighborThreat > threat) {
                        threat = neighborThreat;
                    }
                }
            }
        }
        
        return threat;
    }
    
    // called by placeArmies()
    // given a number of armies and a set of paths to take over, this function calculates the number of armies required to do so
    // and places them appropriately at the starting country of each path
    // it then returns the number of armies leftover
    protected int placeArmiesOnRoutes(ArrayList<int[]> plan, int numberOfArmies) {
        testChat("placeArmiesOnRoutes", "----- Place Armies On Routes -----");
        
        // we need an associative array to remember the costs we calculate for each path
        // so that in case any paths share a starting country, we don't double count what we've already put there
        Map<Integer, Integer> previousCosts = new HashMap<Integer, Integer>();
        
        for (int i=0; i<plan.size(); i++) { // loop through the entire plan
            int startCountry = plan.get(i)[0]; // the first country on the path we're on
            if (countries[startCountry].getOwner() == ID) { // if we own the starting country, it is an original path, so test it; otherwise, it is a fork-branch, so ignore it at this stage
                // calculateCladeCost() returns the number of armies it will take to conquer this path and all of its forks
                // not accounting for how many armies we have on the starting country
                int cost = calculateCladeCost(plan, i);
                
                testChat("placeArmiesOnRoutes","--- total cost of above clade: " + cost);
                
                // find out if startCountry is a border country, and if so, find out how much we want to place there as we pass
                // we will place this number on the country but will NOT add it to costs, so that it will not be added to reservedArmies
                // on future iterations of the loop, essentially ensuring that it will only be added to startCountry once
                // even if there are multiple paths starting from the same country
                // (findBorderStrength() is also run on every other country in the path and its forks when we calculate the cost for it)
                int borderGarrison = checkBorderStrength(startCountry);
                
                // subtract the number of armies already on the starting country (minus 1) from cost
                // not including any armies we may have placed or reserved there on previous iterations of the loop
                // unless that number would be < 0, in which case, make it 0, because we don't want to try to place negative armies
                int extantArmies = countries[startCountry].getArmies() - 1; // armies actually on the country - 1
                int reservedArmies = 0;
                if (previousCosts.get(startCountry) != null) {
                    reservedArmies = previousCosts.get(startCountry); // total costs we calculated we needed for that country on previous iterations (including any armies that were already there at the beginning)
                }
                int discountArmies = extantArmies - reservedArmies; // armies to discount are the armies on the country minus any armies we've already reserved there
                int armiesToPlace = Math.max(0,cost + borderGarrison - discountArmies); // subtract discountArmies from cost, but if that's negative, make it 0
                
                // armiesToPlace should not be greater than numberOfArmies
                armiesToPlace = Math.min(numberOfArmies, armiesToPlace);
                
                testChat("placeArmiesOnRoutes","extantArmies: " + extantArmies + "\n                  reservedArmies: " + reservedArmies + "\n                  discountArmies: " + discountArmies + "\n                  Border garrison: " + borderGarrison);
                testChat("placeArmiesOnRoutes","--- amount we're actually putting on it: " + armiesToPlace);
                
                // place armiesToPlace on the starting country of the path
                // and subtract armiesToPlace from numberOfArmies
                // if it is <= 0, we already have enough armies there, so we don't need to place any
                board.placeArmies(armiesToPlace, startCountry);
                numberOfArmies -= armiesToPlace;
                    
                // if numberOfArmies is <= 0, we've used up all the armies, so break the loop, we're done
                if (numberOfArmies <= 0) {
                    break;
                }
                
                // add the cost we calculated (NOT the adjusted figure, armiesToPlace) to the previousCosts hashmap entry for startCountry
                // so that future iterations of the loop can check it
                // this acts as a sort of "reservation", so that when future iterations of the loop
                // (in the case of forks that leave from the same starting country) adjust their costs to the extant armies
                // they don't count the ones we just placed there
                if (previousCosts.get(startCountry) == null) {
                    previousCosts.put(startCountry, cost);
                } else {
                    previousCosts.put(startCountry, previousCosts.get(startCountry) + cost);
                }

            }
        }

        testChat("placeArmiesOnRoutes", "number of armies left: " + numberOfArmies);
        
        // return the number of armies we have left
        return numberOfArmies;
    }
    
    // calculates the cost of taking over a clade
    // a clade is a monophyletic tree of paths, i.e. a path and all of its forks (and all of their forks, etc.)
    // clades are not represented in our code as single objects, so this function must find the forks of the initial path itself;
    // the function is passed the entire battle plan ("plan"), which may contain multiple clades, and an index, which tells it which original path in the plan to work on;
    // it follows that original path only, and finds all of its forks, and calls itself recursively to find all of its forks' forks, etc.
    // and adds up all of their costs together and returns that number
    protected int calculateCladeCost(ArrayList<int[]> plan, int index) {
        int cost = 0;
        int[] path = plan.get(index);
        cost += getPathCostWithBorders(path); // calculate the cost of the main path
        
        chatCountryNames("calculateCladeCost", path);
        testChat("calculateCladeCost","Cost to take over the above path: " + cost);
        
        for (int i=1; i<path.length; i++) { // loop through the main path to check to see if each country is a branch point for a fork
            for (int j=0; j<plan.size(); j++) { // loop through the rest of the paths in the plan to find any that begin at this country
                if (path[i] == plan.get(j)[0]) { // if one does, it is a fork
                    cost += calculateCladeCost(plan, j); // so recurse, and add that result to the cost
                }
            }
        }
        
        return cost;
    }
    
    // calculate the number of armies it will require to take over a whole path (not including the starting country, which we assume we own)
    // does not subtract the number of armies we already have on the first country in the path
    // does not account for any forks, only calculates over a single path
    // note that "cost" in this function is not simply the number of enemy armies in the way
    // rather, it is an estimate of how many armies it will actually take to conquer the given path
    // *** including the cost of any border garrisons we will want to leave along the way ***
    // calls getPathCost()
    protected int getPathCostWithBorders(int[] path) {
        // get the cost of actually taking over the path
        int cost = getPathCost(path);
        
        // add the cost of any border garrisons we want to leave along the way
        for (int i=1; i<path.length; i++) { // loop through the path, beginning on the SECOND country
            cost += checkBorderStrength(path[i]); // check if we want to leave any armies on this country as a border garrison, add that value to cost
        }

        return cost;
    }
    
    // calculate the number of armies it will require to take over a whole path (not including the starting country, which we assume we own)
    // does not subtract the number of armies we already have on the first country in the path
    // does not account for any forks, only calculates over a single path
    // note that "cost" in this function is not simply the number of enemy armies in the way
    // rather, it is an estimate of how many armies it will actually take to conquer the given path
    protected int getPathCost(int[] path) {
        double cost = 0;
        for (int i=1; i<path.length-1; i++) { // loop through the path, beginning on the SECOND country and ending on the SECOND TO LAST country (we'll do the last country separately after the loop)
            // this is the (approximated) formula to calculate the number of armies needed to win an intermediate battle (one not at the end of a path, so the attacker always gets to roll 3 dice) with 78% certainty (the choice to use 78% was just a judgment call)
            int defenders = countries[path[i]].getArmies(); // enemy armies on this country
            cost += (7161d / 8391d) * (double) defenders + (1.3316d * Math.pow((double) defenders,.4665d));
        }
        // now get the cost for the last battle in the path at 78% certainty (the formula is different because this is a terminal battle (at the end of a path) so the attacker may have to roll 2 dice or 1 die near the end of the battle)
        if (path.length > 1) {
            int defenders = countries[path[path.length-1]].getArmies(); // the enemy armies on the last country
            cost += (7161d / 8391d) * (double) defenders + (1.7273d * Math.pow((double) defenders,.4301d));
        }
        
        return (int) Math.round(cost); // round to the nearest integer and return
    }
    
    // when first estimating the cost of taking over an area without calculating all the actual attack paths,
    // we will package all the unowned countries into an int[] and call this function
    // it adds a dummy element to the beginning of the array and calls getPathCost() to get an estimate of the
    // cost of taking over the area. The dummy element is required, because getPathCost() assumes the first
    // country in the array is one we own, which it usually is, but in this case is not
    // this may seem kludgy, but the alternatives are all worse
    protected int getGlobCost(int[] glob) {
        int[] dummyGlob = new int[glob.length + 1];
        dummyGlob[0] = -1;
        System.arraycopy(glob, 0, dummyGlob, 1, glob.length);
        
        return getPathCost(dummyGlob);
    }
    
    // note: I wrote the following ugly crap intending to make use of it in the placeArmiesOnRoutes() function
    //       but decided to go another way. I'll leave it here in case it's needed later (god, I hope not)
    //
    // given an entire takeover plan (plan) and the index of a single path within it,
    // returns true if the starting country of that path is not found in any other path,
    // OR if it is the first of multiple original paths that share the same starting country
    // in other words, it should return true for every path that is not a fork-branch of another path
    // but since some paths fork from their first element, in which case there would be multiple paths starting from the same country (which we should already own),
    // we call the first one of those we come across an original path (true), and the following ones we call forks (false)
    protected boolean isOriginalPath(ArrayList<int[]> plan, int index) {
        int startCountry = plan.get(index)[0]; // the starting country of the path we're testing
        
        for (int i=0; i<plan.size(); i++) { // loop through the entire plan
            if (i != index) { // don't check against the path we're testing
                int[] checkPath = plan.get(i);
                for (int j=0; j<checkPath.length; j++) { // loop through the path
                    if (checkPath[j] == startCountry) { // if we found startCountry in this path
                        // if the match occurs at the beginning of a path that appears after the path we're testing
                        // then that match doesn't count. the reason for this is that in the case of forks that happen
                        // at the beginning of a pair (or more) of paths, we need to treat one of them as the original,
                        // and the rest as forks; we've chosen to treat the first one as the original in that case.
                        // so the following if-statement rules out the first one by only returning false if the match we found
                        // occurs either in the middle of a path or at the beginning of a PRIOR path only, and NOT at the beginning of a subsequent path
                        if (j!=0 || i < index) {
                            return false;
                        }
                    }
                }
            }
        }
        
        // if we're here, we didn't find anything, so it's an original path; return true
        return true;
    }
    
    // will return a set of all possible terminal attack paths from a country (optionally supplied by passing startCountry)
    // if startCountry is not provided, will find paths from all the starting countries we own in the countryList
    // if we don't own any countries in the countryList, we'll find one nearby to start on
    protected ArrayList getAreaTakeoverPaths(int[] countryList) {
        testChat("getAreaTakeoverPaths", "-- GET AREA TAKEOVER PATHS --");
        testChat("getAreaTakeoverPaths", "startCountry not given");
        
        // we'll store all candidate paths here (individual paths are integer arrays)
        ArrayList<int[]> paths = new ArrayList<int[]>();
        
        // a global variable we need in order to keep track of how many paths
        // have been found so far
        pathCount = 0;

        // we'll test every path from every country we own in the countryList
        // if we don't own any countries in countryList, we'll find a country close-by to start from
        
        int[] candidates = getPlayerCountriesInArea(countryList); // get countries in countryList that we own

        if (candidates.length > 0) { // if we own any countries in countryList
            
            // just testing to see if we found the countries we own
            String[] countryNames = getCountryNames(candidates);
            testChat("getAreaTakeoverPaths", "countries we own in area: " + Arrays.toString(countryNames));
            
            // loop through candidates array, finding paths for each of them
            int[] initialPath = new int[1];
            for (int i=0; i<candidates.length; i++) {
                initialPath[0] = candidates[i];
                paths.addAll(findAreaPaths(initialPath, countryList)); // concatenate results from all of them together in the paths ArrayList
            }
        }
        else { // we don't own any countries in countryList
            testChat("getAreaTakeoverPaths", "we don't own any countries in area");
            
            // find the cheapest path to it from some country we own
            // we pass 'false' as the second parameter to tell the function that we don't care
            // how many armies are on the border of the area that we end up at; we only care about
            // the cost of the path up until that point, since we're planning on taking over the whole area anyway
            int[] initialPath = getCheapestRouteToArea(countryList, false);
            
            if (initialPath != null) {
                String[] countryNames = getCountryNames(initialPath);
                testChat("getAreaTakeoverPaths", "Path to continent: " + Arrays.toString(countryNames));
                
                // use that as starting country
                paths = findAreaPaths(initialPath, countryList);
            } else {
                // if we're here, we couldn't find a country we own that can reach countryList for some reason
                // so all we can do is return empty-handed
                System.out.println("ERROR in getAreaTakeoverPaths() - could not get initial path to area");
                return paths;
            }
        }
        
        testChat("getAreaTakeoverPaths", "pathCount: " + pathCount);
        
        // now we have a complete list of terminal paths from every country we own in the area (countryList)
        // (or from a nearby country in the case that we don't own any in the area itself)
        // but we still need to go over all of the countries we have paths to, and see if any are left out.
        // this will happen in the case of non-contigious areas, i.e. "you can't get there from here"
        // or it may also happen if we hit the <pathCount> limit in findAreaPaths() before we got to everything;
        // so we'll check if any of the countries in countryList are not in any of the paths we found
        // and repackage just them as a new area, call getAreaTakeoverPaths() recursively on that area,
        // and add the results to the paths ArrayList
        ArrayList<Integer> countriesLeft = new ArrayList<Integer>(); // (note that countriesLeft is an ArrayList, which is atypical (usually integer arrays are used))
        for (int i=0; i<countryList.length; i++) { // create ArrayList of whole area
            countriesLeft.add(countryList[i]);
        }
        int pathsSize = paths.size();
        iLoop: for (int i=0; i<pathsSize; i++) { // loop through all paths
            int[] path = paths.get(i);
            for (int j=0; j<path.length; j++) { // loop through all countries in path
                for (int k=0; k<countriesLeft.size(); k++) { // loop through all countries in area that we haven't seen yet (that's what countriesLeft represents)
                    if (path[j] == countriesLeft.get(k)) { // if this country is in a path
                        countriesLeft.remove(k); // remove it from countriesLeft
                        break; // and continue checking the rest of them
                    }
                }
                if (countriesLeft.size() == 0) { // if this is empty, every country in area is accounted for in paths
                    break iLoop; // so stop looking, we're done
                }
            }
        }
        
        // now anything left in countriesLeft was not accounted for in any path we found
        // (which could happen if the original area we searched was not contiguous and we didn't own a country in at least one of the discrete parts,
        // or if we had to stop when we hit the <pathCount> limit before we got to all the countries)
        // so convert it into an integer array and pass it into getAreaTakeoverPaths() recursively as a new area to search
        // then we'll add the results of that function call to the paths we already found
        if (countriesLeft.size() > 0) {
            int[] countriesLeftArray = new int[countriesLeft.size()];
            for (int i=0; i<countriesLeftArray.length; i++) {
                countriesLeftArray[i] = countriesLeft.get(i).intValue();
            }
            String[] countryNames = getCountryNames(countriesLeftArray);
            
            testChat("getAreaTakeoverPaths", "There are " + countriesLeftArray.length + " countries left");
            //testChat("getAreaTakeoverPaths", "countriesLeft: " + Arrays.toString(countryNames));
            
            testChat("getAreaTakeoverPaths", "===========>> RECURSE on countriesLeft");
            paths.addAll(getAreaTakeoverPaths(countriesLeftArray));
            testChat("getAreaTakeoverPaths", "<<=========== END RECURSE");

        } else {
            testChat("getAreaTakeoverPaths", "all countries were accounted for in the list of paths we found");
        }
        
        // now we will add single-country paths for each country we own
        // these aren't terminal paths, of course; they're kind of dummy paths
        // they will be added to the battlePlan, from which the border countries among them will be armed;
        // by treating the border countries as paths, we harness a lot of existing logic for placing armies
        for (int country : countryList) {
            if (getProjectedCountryOwner(country) == ID) {
                paths.add(new int[]{country});
            }
        }
        
        testChat("getAreaTakeoverPaths", "There are " + paths.size() + " terminal paths");
        //chatCountryNames("getAreaTakeoverPaths", paths);
        
        // return the paths
        return paths;
    }
    // overload getAreaTakeoverPaths to allow a double parameter version
    // in this version, a starting country is supplied, and we'll only search for paths starting from that country
    protected ArrayList getAreaTakeoverPaths(int[] countryList, int startCountry) {
        testChat("getAreaTakeoverPaths", "-- GET AREA TAKEOVER PATHS --");
        testChat("getAreaTakeoverPaths", "startCountry is " + startCountry);
        
        // we'll store all candidate paths here (individual paths are integer arrays)
        ArrayList<int[]> paths = new ArrayList<int[]>();
        
        // create an new history (integer array) containing only the starting country
        int[] initialPath = new int[1];
        initialPath[0] = startCountry;
        
        // find paths
        paths = findAreaPaths(initialPath, countryList);
        
        // return the paths
        return paths;
    }
    
    // find the nearest country owned by owner and return the cheapest path
    // from it to a country in the given continent
    // just packages all the countries in that continent into an area and calls getCheapestRouteToArea
    protected int[] getCheapestRouteToCont(int cont, boolean into, int owner) {
        return getCheapestRouteToArea(getCountriesInContinent(cont), into, owner);
    }
    // overload getCheapestRouteToCont to allow a single parameter version
    // if no ID is provided, assume it should be the owner
    protected int[] getCheapestRouteToCont(int cont, boolean into) {
        return getCheapestRouteToCont(cont, into, ID);
    }
                              
    // find the cheapest path from any country owned by owner
    // to a country in the given area (area could be a continent, for example).
    // the boolean parameter "into" tells the function whether to take into account the
    // number of armies on the area's border country at the end of the path.
    // if "into" is true, we add that country's armies to the cost; if false, we don't
    // so that when we're using this function to knockout an enemy bonus, we find the weakest border (all else being equal)
    // but when we're taking over the whole continent, we simply find the cheapest path to the continent
    // without regard to how many countries are on the border we end up at
    // this is a modified version of the BoardHelper method cheapestRouteFromOwnerToCont()
    protected int[] getCheapestRouteToArea(int[] area, boolean into, int owner) {
        
        String[] countryNames = getCountryNames(area);
        testChat("getCheapestRouteToArea", "getCheapestRouteToArea area: " + Arrays.toString(countryNames));
        
        if (owner < 0 || area.length == 0) {
            System.out.println("ERROR in getCheapestRouteToArea() -> bad parameters");
            return null;
        }
        
        // first, check to see if we already own a country in the area.
        int[] ownedCountries = getPlayerCountriesInArea(area, owner);
        if (ownedCountries.length > 0) {
            // the player owns a country in the area already. That country itself is the cheapest route
            // so simply return the first one in the list of owned countries
            return new int[] { ownedCountries[0] };
        }
        
        // We keep track of which countries we have already seen (so we don't
        // consider the same country twice). We do it with a boolean array, with
        // a true/false value for each of the countries:
        boolean[] haveSeenAlready = new boolean[countries.length];
        for (int i = 0; i < countries.length; i++)
        {
            haveSeenAlready[i] = false;
        }
        
        // Create a Q (with a history) to store the country-codes and their cost
        // so far:
        CountryPathStack Q = new CountryPathStack();
        
        // We explore from all the borders of <area>
        int testCode, armiesSoFar;
        int[] testCodeHistory;
        int[] borderCodes = getAreaBorders(area);
        for (int i = 0; i < borderCodes.length; i++) {
            testCode = borderCodes[i];
            if (into == true) { // if we care about finding the weakest border
                armiesSoFar = countries[borderCodes[i]].getArmies(); // add the armies of the starting country to the cost
            } else { // if we don't
                armiesSoFar = 0; // start with a cost of 0
            }
            testCodeHistory = new int[1];
            testCodeHistory[0] = testCode;
            haveSeenAlready[testCode] = true;
            
            Q.pushWithValueAndHistory(countries[borderCodes[i]], armiesSoFar, testCodeHistory );
        }
        
        // So now we have all the area borders in the Q
        // (all with either cost 0, or the cost of the armies on the border country, depending on the value of "into"),
        // expand every possible outward path (in the order of cost).
        // eventually we should find a country owned by <owner>,
        // then we return that path's history
        while ( true ) {
            armiesSoFar = Q.topValue();
            testCodeHistory = Q.topHistory();
            testCode = Q.pop();
            
            // if we own <testCode> or if we are planning to this turn
            if ( getProjectedCountryOwner(testCode) == owner ) {
                // we have found the best path. return it
                return testCodeHistory;
            }
            
            int[] canAttackInto = BoardHelper.getAttackList(countries[testCode], countries);
            
            for (int i=0; i<canAttackInto.length; i++) {
                if (!haveSeenAlready[canAttackInto[i]]) {
                    // Create the new node's history array. (It is just 
                    // testCode's history with its CC added at the beginning):
                    int[] newHistory = new int[ testCodeHistory.length + 1 ];
                    newHistory[0] = canAttackInto[i];
                    for (int j = 1; j < newHistory.length; j++) {
                        newHistory[j] = testCodeHistory[j-1];
                    }
                    Q.pushWithValueAndHistory( 
                                              countries[canAttackInto[i]], 
                                              // If the neighbor is owned by the proper person then subtract
                                              // its armies from the value so it gets pulled off the Q next.
                                              // Without this there is a bug					
                                              armiesSoFar + (getProjectedCountryOwner(canAttackInto[i]) == owner ? -getProjectedArmies(canAttackInto[i]) : getProjectedArmies(canAttackInto[i])),
                                              newHistory );
                    haveSeenAlready[ countries[canAttackInto[i]].getCode() ] = true;
                }
            }
            
            // as far as we know, this should only happen in maps with one-way connections
            // if the only country owned by owner is trapped behind a one-way connection from the area
            if (Q.isEmpty()) {
                System.out.println("ERROR in getCheapestRouteToArea - could not find a country owned by owner");
                return null;
            }
        }
    }
    // overload getCheapestRouteToArea to allow a single parameter version
    // if no ID is provided, assume it should be the owner
    protected int[] getCheapestRouteToArea(int[] area, boolean into) {
        return getCheapestRouteToArea(area, into, ID);
    }
    
    // called in the placeArmies() phase
    // given that the bot has already chosen an area to pursue, this function should find a comprehensive plan
    // to takeover that continent/area in the form of a set of int[] arrays of country codes that form attack paths from countries we own
    // this function calls getAreaTakeoverPaths() to get a list of all possible takeover paths (allPaths) through a given country list (area)
    // then from those finds a comprehensive set of paths that pass through every enemy country in the area, including forks and islands
    // ideally, it will find as few as possible that contain every enemy country in the area (as well as every country we own, even if that means a dummy path of only 1 country)
    // getAreaTakeoverPaths() adds 1-element-long single-country paths for each country we own in the area, and this function will pick all of those whose country isn't in one of the other paths it picks
    // this is useful in the place armies phase, because those paths are used to arm the border countries
    protected ArrayList pickBestTakeoverPaths(int[] area) {
        
        ArrayList<int[]> checkPaths = getAreaTakeoverPaths(area); // first, get comprehensive list of paths; this could be thousands of elements long in large cases
        
        ArrayList<int[]> results = new ArrayList<int[]>(); // this will hold the results, which could be several paths, to include forks and islands
        ArrayList<Integer> countriesLeft = new ArrayList<Integer>(); // list of countries not in any paths we've chosen so far
        // initially populate countriesLeft with every country in area
        for (int i=0; i<area.length; i++) {
                countriesLeft.add(area[i]);
        }
        
        testChat("pickBestTakeoverPaths", "-- PICK BEST TAKEOVER PATHS --");
        
        // now the meat:
        // in each iteration of this loop, we'll pick a new path out of checkPaths to put in the results arraylist.
        // then, if we've already chosen other paths, we'll need to prune the one we just picked of any overlap with them.
        // there should only ever be overlap at the beginning of the new path, in which case it will be a branch of a fork,
        // so we'll want to store the path only the branch point forward (the place armies and attack phase will know how to deal with that).
        // so after we do all that, next we'll prune the list of potential paths (checkPaths) of any whose last element is contained somewhere within one of the results paths we've already chosen.
        // that way, next time around the loop, we're guaranteed to choose a path that ends in a country we haven't covered yet.
        // finally, we'll prune the countriesLeft arraylist of any countries covered in the path we just picked, so that it only contains countries that aren't in any of the paths we've chosen so far.
        // when countriesLeft is empty, we'll know that we've found a comprehensive set of paths to take over the area, so we'll be done with the loop.
        while (countriesLeft.size() > 0) {
            testChat("pickBestTakeoverPaths", "-");
            
            // find the best single path from the pruned list of paths to check
            int[] newPath = findBestSingleTakeoverPath(checkPaths, area); // see findBestSingleTakeoverPath() for the criteria we use to pick the best path
            
            // check newPath against all the paths in results to see if it should be a fork of any of them
            // if it should, trim the beginning of the path so that its first element is the branch point
            int[] newPathCut = trimFork(results, newPath);
            
            // add truncated array to results
            results.add(newPathCut);

            testChat("pickBestTakeoverPaths", "-- Paths we're picking:");
            chatCountryNames("pickBestTakeoverPaths", results);
            
            // prune checkPaths
            // find all paths in checkPaths whose last element is not found anywhere in any chosen path
            // keep those and throw away the rest
            ArrayList<int[]> prunedPaths = new ArrayList<int[]>();
            int checkPathsSize = checkPaths.size();
            for (int i=0; i<checkPathsSize; i++) {
                boolean isMatch = false;
                int[] thisCheckPath = checkPaths.get(i); // the path in checkPaths we're testing this loop
                jLoop: for (int j=0; j<results.size(); j++) { // loop through all the paths in results
                    int[] resultsPath = results.get(j); // the path in results we're checking against this loop
                    for (int k=0; k<resultsPath.length; k++) { // loop through this path in results
                        if (thisCheckPath[thisCheckPath.length-1] == resultsPath[k]) { // if the last element in thisCheckPath is in resultsPath
                            isMatch = true;
                            break jLoop; // move on to next path in checkPaths
                        }
                    }
                }
                if (!isMatch) {
                    prunedPaths.add(thisCheckPath);
                }
            }
            
            checkPaths = prunedPaths;

            testChat("pickBestTakeoverPaths", "-- Pruned list of paths:");
            chatCountryNames("pickBestTakeoverPaths", checkPaths);

            // remove any countries in countriesLeft that are in any of the results paths
            Iterator<Integer> countriesLeftIterator = countriesLeft.iterator();
            while (countriesLeftIterator.hasNext()) { // loop through countriesLeft
                int thisCountry = countriesLeftIterator.next();
                iLoop2: for (int i=0; i<results.size(); i++) { // loop through results list
                    int[] thisResult = results.get(i);
                    for (int j=0; j<thisResult.length; j++) { // loop through this result path
                        if (thisResult[j] == thisCountry) { // if this country in countriesLeft is in a results array
                            countriesLeftIterator.remove(); // then remove it from countriesLeft
                            break iLoop2; // and skip to the next country in countriesLeft
                        }
                    }
                }
            }
            
            testChat("pickBestTakeoverPaths", "-- Pruned version of countriesLeft:");
            if (countriesLeft.size() > 0) {
                chatCountryNames("pickBestTakeoverPaths", countriesLeft);
            } else {
                testChat("pickBestTakeoverPaths", "[] - no countries in countriesLeft");
            }
        }

        return results;
    }
    
    protected int[] trimFork(ArrayList<int[]> checkPlan, int[] pathToTrim) {
    
        // now we search for a common element with any of the paths we've already chosen
        // if we find one, this will be a branch point (fork), so we'll truncate everything from the new path before the branch point
        int newStart = 0;
        iLoop: for (int i=pathToTrim.length-1; i>=0; i--) { // iterate backwards through pathToTrim
            for (int[] checkPlanPath : checkPlan) { // loop through all the paths we've already picked
                for (int checkPlanCountry : checkPlanPath) { // loop through this path we've already picked
                    if (pathToTrim[i] == checkPlanCountry) { // if this country in pathToTrim is in one of the old paths
                        newStart = i; // store the index
                        break iLoop; // quit searching
                    }
                }
            }
        }
        // now truncate the beginning of pathToTrim
        // if we didn't find a branch point, it will not be affected
        int[] trimmed = new int[pathToTrim.length - newStart]; // make new array of appropriate length
        System.arraycopy(pathToTrim, newStart, trimmed, 0, trimmed.length); // copy end of pathToTrim into it
        
        return trimmed;
    }
    
    // given a list of takeover paths, pick the longest one
    // if there are multiple longest ones (which there often are),
    // find one that ends on a border and return that one
    // in future versions, we may want to be more sophisticated about which one to choose
    protected int[] findBestSingleTakeoverPath(ArrayList<int[]> paths, int[] area) {
        // find the length of the longest path
        int maxPathLength = 0;
        int size = paths.size();
        for (int i=0; i<size; i++) {
            if (paths.get(i).length > maxPathLength) {
                maxPathLength = paths.get(i).length;
            }
        }
        
        // populate a new arraylist with all the longest paths
        ArrayList<int[]> longestPaths = new ArrayList<int[]>();
        for (int i=0; i<size; i++) {
            if (paths.get(i).length == maxPathLength) {
                longestPaths.add(paths.get(i));
            }
        }
        
        // pick a path that ends in a border, if there is one
        size = longestPaths.size();
        int pathLength;
        boolean isBorder;
        testChat("findBestSingleTakeoverPath", "--- Longest paths: ---");
        for (int i=0; i<size; i++) {
            pathLength = longestPaths.get(i).length;
            isBorder = isAreaBorder(longestPaths.get(i)[pathLength-1],area);
            
            String[] countryNames = getCountryNames(longestPaths.get(i));
            testChat("findBestSingleTakeoverPath", Arrays.toString(countryNames) + " border? " + isBorder);
            
            // for now, we'll just return the first one we find that ends in a border
            if (isBorder) {
                return longestPaths.get(i);
            }
        }
        
        // if we get here, none of the longest paths ended on a border, so just return the first one
        return longestPaths.get(0);
    }
    
    // checks to see if country is a border of area by seeing if any of its neighbors is outside of area
    // if the country itself is not in the area, returns false
    protected boolean isAreaBorder (int country, int[] area) {
        // if <country> is not in the area, return false
        if (!isInArray(country, area)) {
            return false;
        }
        
        int[] neighbors = BoardHelper.getAttackList(countries[country],countries); // countries[country].getAdjoiningCodeList(); // get neighbors
        boolean inArea = false;
        
        for (int i=0; i<neighbors.length; i++) { // loop through all the country's neighbors
            inArea = false;
            for (int j=0; j<area.length; j++) { // loop through every country in area
                if (neighbors[i] == area[j]) { // if we found this neighbor in the area
                    inArea = true;
                    break;
                }
            }
            if (!inArea) { // if inArea is false, then this neighbor is not in the area
                return true; // which means country is a border, so return true
            }
        }
        return false; // if we got here, all of the neighbors were in area, so country is not a border; return false
    }
    // overloaded version to handle arraylists
    protected boolean isAreaBorder(int country, ArrayList<Integer> list) {
        return isAreaBorder(country, convertListToIntArray(list));
    }
    
    // returns an int[] of all the borders of the given area
    protected int[] getAreaBorders(int[] area) {
        ArrayList<Integer> borders = new ArrayList<Integer>();
        
        for (int i=0; i<area.length; i++) {
            if (isAreaBorder(area[i],area)) {
                borders.add(area[i]);
            }
        }
        
        int[] bordersArray = new int[borders.size()];
        for (int i=0; i<bordersArray.length; i++) {
            bordersArray[i] = borders.get(i);
        }
        
        return bordersArray;
    }
    // overloaded version to handle arraylists
    protected int[] getAreaBorders(ArrayList<Integer> list) {
        return getAreaBorders(convertListToIntArray(list));
    }
    
    // find all possible paths through enemy countries within countryList
    // starting with the last country in the history array
    // history is an array of country codes containing the path history already searched
    // countryList is an array of country codes in which the entire search takes place
    // this may typically be a continent, but doesn't have to be
    // returns an ArrayList of paths (which are integer arrays)
    // the function calls itself recursively
    protected ArrayList findAreaPaths(int[] history, int[] countryList) {
        ArrayList<int[]> terminalPaths = new ArrayList<int[]>(); // all possible terminal paths will end up in this array
        int startCountry = history[history.length - 1]; // starting country is the last element in the history
        int[] newHistory = new int[history.length + 1]; // new history array to add the next country(s) to
        System.arraycopy(history, 0, newHistory, 0, history.length); // copy the old history into the beginning of new history, leaving one empty spot at the end
        int[] neighbors = countries[startCountry].getAdjoiningCodeList(); // get list of startCountry's neighbors
        boolean anyValidNeighbors = false; // if we find any valid neighbors, we'll switch this to true
        
        // check the global variable <pathCount>, which stores the total number of paths we've already created;
        // if we've already got a hundred thousand of them, we want to stop looking to save time (and memory);
        // if we didn't find everything, it's okay, because getAreaTakeoverPaths() will take stock of
        // what's missing, and call findAreaPaths() again on whatever's leftover
        if (pathCount >= 100000) {
            return terminalPaths;
        }

        // loop through all neighbors; if valid, add to history and recurse
        for (int i=0; i<neighbors.length; i++) {
            if (pathNeighborIsValid(neighbors[i], history, countryList)) { // if the country is valid
                anyValidNeighbors = true;
                newHistory[newHistory.length-1] = neighbors[i]; // add it to the end of the new history
                terminalPaths.addAll(findAreaPaths(newHistory, countryList)); // recurse, adding whole chain to the terminalPaths array
            }
        }
        
        // if there were no valid neighbors, we're at the end of the path
        // the history as given includes the terminal country of the path
        // so all we have to do is send back the history we were given, wrapped in an arrayList
        // (we'll add it to terminalPaths, which should be empty in this case)
        // and as it bubbles up, it will be concatenated with any other terminal paths that were found
        // in other branches
        if (anyValidNeighbors == false) {
            // make a copy of history to add to terminalPaths to avoid reference/scope problem
            int[] historyCopy = new int[history.length];
            System.arraycopy(history, 0, historyCopy, 0, history.length);
            
            // since we're adding a terminal path here, increment <pathCount>
            // which keeps track of the total number of paths we have
            pathCount++;
            
            // add the path
            terminalPaths.add(historyCopy);
        }
        
        // return the terminalPaths arrayList. if we're at the end of a path, this will contain
        // a single terminal path. If we're not at the end of a path, it will contain all the terminal
        // paths below us that were found recursively, which will then be concatenated with any other
        // terminal paths that were found elsewhere (i.e. the branches that split above us) as they bubble up
        return terminalPaths;
    }
    
    //given a list of countries, clump the countries into all possible contiguous areas
    protected ArrayList findContiguousAreas(int[] countryArray) {
        ArrayList<int[]> contiguousAreaList = new ArrayList<int[]>();
        ArrayList<Integer> countryList = new ArrayList<Integer>();
        for (int country : countryArray) { countryList.add(country); }
        
        // loop through the master <countryList>
        while (countryList.size() > 0) {
            
            ArrayList<Integer> thisClump = new ArrayList<Integer>(); // will be populated with this clump of contiguous countries
            thisClump.add(countryList.get(0)); // add the country we're on to the clump
            countryList.remove(0); // then remove it from the master list
            
            // now we'll find all the neighbors of the initial country,
            // and if any of them are in <countryList>, we'll add them to the end of the <thisClump> list
            // and remove them from <countryList>;
            // since we added them to <thisClump>, we'll then run into them on subsequent iterations
            // of the for loop and check their neighbors as well in the same fashion;
            // this loop will end when no more countries in it have neighbors that are in <countryList>
            // at which point we know we'll have an exhaustive "clump" of contiguous countries,
            // and then we'll put it into our results list <contiguousAreaList>
            // and move on to the next country in the master list
            for(int country=0; country<thisClump.size(); country++) { // loop through the countries in this clump
                int[] neighbors = countries[thisClump.get(country)].getAdjoiningCodeList(); // get neighbors of this country
                for (int neighbor : neighbors) { // loop through neighbors
                    if (isInArray(neighbor,countryList)) { // if neighbor is in countryList, we haven't seen it yet,
                        thisClump.add(neighbor); // so add neighbor to thisClump
                        countryList.remove((Integer) neighbor); // and remove it from countryList
                    }
                }
            }
            
            // add <thisClump> to the results list
            contiguousAreaList.add(convertListToIntArray(thisClump));
        }
        
        return contiguousAreaList;
    }
    
    // called by the findAreaPaths function to determine whether a potential country in a path is valid
    // i.e. we don't own it, it hasn't been visited already, and it's in the specified list of countries (e.g. a certain continent)
    protected boolean pathNeighborIsValid(int neighbor, int[] history, int[] countryList) {

        // first check if we own the country using getProjectedCountryOwner();
        // importantly, we're checking if we actually own the country right now,
        // but we're also treating it as though we own it if it's in a path that's already in <battlePlan>;
        // in other words, if we're already planning to take over a country this turn (for some other purpose)
        // we're going to treat it as though we own it here; that way the paths we're finding here
        // will take into account the paths we will have already taken over by the time we get to them
        if (getProjectedCountryOwner(neighbor) == ID) {
            return false; // if we own it, it's invalid, so return false immediately
        }

        // next, check if the neighbor has already been visited (i.e. it's in the history)
        // it's probably faster to iterate backwards from the end of the array, since a neighbor
        // we've already visited is more likely to be near the end of the array
        for (int i=history.length-1; i>=0; i--) {
            if (history[i] == neighbor) {
                return false; // if in history, it's invalid, so return false immediately
            }
        }
        
        // check if the neighbor is in the allowed list of countries
        for (int i=0; i<countryList.length; i++) {
            if (countryList[i] == neighbor) {
                return true; // if we've gotten this far, all the other checks have passed, so return true
            }
        }
        
        // return false by default. We'll get here in one of two cases I can think of:
        // (1) it's an enemy country that's not in the history and is also not in the countryList, or
        // (2) it's not a country. In case we get passed values that aren't countries, they should be deemed invalid
        return false;
    }

    // method to concatenate integer arrays
    // THIS METHOD HASN'T BEEN TESTED YET!
    protected int[] arrayConcat(int[] a, int[] b) {
        int aLength = a.length;
        int bLength = b.length;
        int[] c= new int[aLength + bLength];
        System.arraycopy(a, 0, c, 0, aLength);
        System.arraycopy(b, 0, c, aLength, bLength);
        return c;
    }
    
    // takes an integer array of continent codes and returns a string array of the associated continent names
    // useful for testing purposes
    protected String[] getContinentNames(int[] codes) {
        int size = codes.length;
        String[] names = new String[size];
        for (int i=0; i<size; i++) {
            names[i] = board.getContinentName((Integer) codes[i]).replace(",",""); // get rid of commas in country names because that's confusing when we output the whole array as a string
        }
        return names;
    }
    
    // takes an integer array of country codes and returns a string array of the associated country names
    // useful for testing purposes
    protected String[] getCountryNames(int[] codes) {
        int size = 0;
        try {
            size = codes.length;
        } catch (NullPointerException e) {
            System.err.println("Viking says: NullPointerException: " + e.getMessage());
            e.printStackTrace();
            return new String[]{"[error: null]"};
        }
        String[] names = new String[size];
        for (int i=0; i<size; i++) {
            names[i] = countries[codes[i]].getName().replace(",",""); // get rid of commas in country names because that's confusing when we output the whole array as a string
        }
        return names;
    }
    // overloaded version to handle arraylists instead of arrays
    protected String[] getCountryNames(ArrayList<Integer> list) {
        return getCountryNames(convertListToIntArray(list));
    }
    
    // return the name of a country code
    protected String getCountryName(int code) {
        String name;
        if (code >= 0 && code < countries.length) {
            name = countries[code].getName().replace(",",""); // get rid of commas in country name
        } else {
            name = "" + code;
        }
        return name;
    }

    // chat a list of countries by name, given an array of country codes
    // callingFunc is simply a string to give to the testChat() function to tell it where the chatting is coming from; see testChat() for details
    // useful for testing purposes
    protected void chatCountryNames(String callingFunc, int[] codes) {
        if (codes.length < 1) { return; } // if we're passed an empty array, simply do nothing
        String[] countryNames = getCountryNames(codes);
        testChat(callingFunc, Arrays.toString(countryNames));
    }
    // chat a list, either of arrays of countries (the usual case), or simply of countries (unusual)
    // converting, in both cases, the country codes to country names
    // **NOTE!** this function assumes that all the elements of list are the same type. It may act unexpectedly if they are not
    // callingFunc is simply a string to give to the testChat() function to tell it where the chatting is coming from; see testChat() for details
    // useful for testing purposes
    protected void chatCountryNames(String callingFunc, ArrayList list) {
        if (list.size() < 1) { return; } // if we're passed an empty ArrayList, simply do nothing
        // if the first element in list is an int[], we'll assume they all are, and this is a list of paths
        // so we'll loop through list and chat out each path separately
        if (list.get(0) instanceof int[]) {
            for(int i=0; i<list.size(); i++) {
                if (list.get(i) instanceof int[]) {
                    int[] codesArray = (int[]) list.get(i);
                    chatCountryNames(callingFunc, codesArray);
                }
            }
        }
        // if the first element in list is an integer, we'll assume they all are, and this is a list of countries
        // so we'll convert list into an int[], and chat it out all at once
        if (list.get(0) instanceof Integer) {
            // first convert the ArrayList into an int[] array
            int[] codesArray = new int[list.size()];
            for (int i=0; i<codesArray.length; i++) {
                if (list.get(i) instanceof Integer) {
                    codesArray[i] = (Integer) list.get(i);
                } else {
                    codesArray[i] = -1;
                }
            }
            // then call chatCountryNames using the int[] array
            chatCountryNames(callingFunc, codesArray);
        }
    }
    
    // function to chat Objectives for testing purposes
    // objectives are hashmaps of an attack plan and other information for taking over areas or knocking out bonuses or what have you
    protected void chatObjectives(String callingFunc, HashMap<String, Object> objective) {
        String message = new String();
        for (String key : objective.keySet()) {
            Object value = objective.get(key);
            String stringValue = objectToString(value);
            
            if (key == "continentID") {
                message += "continent: " + board.getContinentName((Integer) value) + "\n";
            } else if (key == "continentIDs") {
                message += "continents: " + Arrays.toString(getContinentNames((int[]) value)) + "\n";
            } else {
                message += key + ": " + stringValue + "\n";
            }
        }
        
        testChat(callingFunc, message);
    }
    // overloaded version to handle arraylists of Objectives
    protected void chatObjectives(String callingFunc, ArrayList<HashMap> list) {
        for (HashMap objective : list) {
            chatObjectives(callingFunc, objective);
        }
    }
    
    // convert a number of different types to String
    // if it can't convert that type, return null
    // this is useful for outputting various values in testing
    protected String objectToString(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Integer || value instanceof Float) {
            return "" + value;
        }
        if (value instanceof int[]) {
            return Arrays.toString(getCountryNames((int[]) value));
        }
        if (value instanceof ArrayList) {
            String listString = new String();
            for (Object element : (ArrayList) value) { // loop through the list
                listString += objectToString(element) + "\n"; // recurse on each element, converting it to a string, whatever type it is
            }
            return listString;
        }
        
        return "";
    }
    
    // helper function to return an array of the countries a player owns in a given continent
    // player is the player ID to check; cont is the continent in question
/*    protected int[] getPlayerCountriesInContinent(int cont, int player) {
        // continent iterator returns all countries in 'cont',
        // player iterator returns all countries out of those owned by 'player'
        // this gives us the list of all the countries we own in the continent
        CountryIterator theCountries = new PlayerIterator(player, new ContinentIterator(cont, countries));
        
        // Put all the countries we own into an ArrayList
        ArrayList countryArray = new ArrayList();
        while (theCountries.hasNext()) {
            countryArray.add(theCountries.next());
        }
        
        // Put the country code of each of the countries into an integer array
        int size = countryArray.size();
        int[] intArray = new int[size];
        for(int i=0; i < size; i++) {
            intArray[i] = ((Country)countryArray.get(i)).getCode();
        }
        
        return intArray;
    }
    // overloaded version: if no player is provided, assume it should be us
    protected int[] getPlayerCountriesInContinent(int cont) {
        return getPlayerCountriesInContinent(cont, ID);
    }*/

    // returns the country code of the weakest enemy country that this country can attack,
    // preferring the one owned by the strongest enemy in the event of a tie;
    // if a neighbor is in <blacklist>, it's ineligible; this function is used for pathfinding, so the blacklist functions as a history of countries already chosen
    // if there are no enemy neighbors, returns -1
    protected int findWeakestNeighborOwnedByStrongestEnemy(int country, ArrayList<Integer> blacklist) {
        int[] neighbors = countries[country].getAdjoiningCodeList(); // get array of neighbors
        ArrayList<Integer> enemyNeighbors = new ArrayList<Integer>();
        
        // loop through all the neighbors, adding all enemy neighbors to the <enemyNeighbors> list
        for (int neighbor : neighbors) { // loop through all neighbors
            // if <country> can attack into <neighbor> and we don't own (or plan to own) <neighbor> and it isn't in the blacklist
            if (countries[country].canGoto(neighbor) && getProjectedCountryOwner(neighbor) != ID && !isInArray(neighbor, blacklist)) {
                enemyNeighbors.add(neighbor); // then add it to the list of enemy neighbors
            }
        }
        
//        testChat("findWeakestNeighborOwnedByStrongestEnemy", "Enemy neighbors of " + getCountryName(country) + ":");
//        chatCountryNames("findWeakestNeighborOwnedByStrongestEnemy", enemyNeighbors);
        
        // remove all but the weakest enemy neighbors from the list;
        // first, loop through the list once to find the least number of armies on any of the countries;
        // then loop again to remove any countries that have more armies than that
        int leastArmies = Integer.MAX_VALUE; // initially set <leastArmies> to the highest possible value
        for (int neighbor : enemyNeighbors) { // loop over neighbors
            int armies = countries[neighbor].getArmies(); // the number of armies on this country
            if (armies < leastArmies) { // if there are fewer armies on this country than <leastArmies>
                leastArmies = armies; // set <leastArmies> to this country's number of armies
            }
        }
        ListIterator<Integer> iter = enemyNeighbors.listIterator(enemyNeighbors.size());
        while (iter.hasPrevious()) { // iterating backwards over the list should be faster when removing elements
            int neighbor = iter.previous(); // this country
            int armies = countries[neighbor].getArmies(); // this country's number of armies
            if (armies > leastArmies) { // if this country has more armies than the weakest neighbor
                iter.remove(); // remove it
            }
        }
        
//        testChat("findWeakestNeighborOwnedByStrongestEnemy", "Weakest enemy neighbors of " + getCountryName(country) + ":");
//        chatCountryNames("findWeakestNeighborOwnedByStrongestEnemy", enemyNeighbors);
        
        // now we should have a list of all of only the weakest enemy neighbors;
        // so we just need to find a country owned by the strongest player
        int chosenCountry = -1;
        int highestIncome = Integer.MIN_VALUE; // we don't want to use 0 here, because it might be (???) technically possible for everyone to have a negative income if there are negative continent bonuses; although the game probably keeps the minimum at 3, I'm not sure
        for (int neighbor : enemyNeighbors) { // loop through the list of weakest neighbors
            int income = board.getPlayerIncome(countries[neighbor].getOwner()); // the income of the player that owns this country
            if (income > highestIncome) { // if it's higher than the highest one we've seen so far
                chosenCountry = neighbor; // choose this country
                highestIncome = income; // this is the new highest income we've seen
            }
        }
        
//        testChat("findWeakestNeighborOwnedByStrongestEnemy","Weakest neighbor owned by strongest enemy: " + getCountryName(chosenCountry));
        
        // return the chosen country
        // if the list of enemy neighbors was empty, this value will be -1
        return chosenCountry;
    }
    // overloaded version to allow the function to be called without a blacklist;
    // just passes a dummy arraylist as the blacklist parameter
    protected int findWeakestNeighborOwnedByStrongestEnemy(int country) {
        return findWeakestNeighborOwnedByStrongestEnemy(country, new ArrayList<Integer>());
    }
    
    // return array of countries (projected to be) owned by <player>
    protected int[] getPlayerCountries(int player) {
        ArrayList<Integer> ownedCountries = new ArrayList<Integer>();
        for (int i=0; i<countries.length; i++) {
            if (getProjectedCountryOwner(i) == player) {
                ownedCountries.add(i);
            }
        }
        return convertListToIntArray(ownedCountries);
    }
    // overloaded version: if no player is supplied, assume it should be us
    protected int[] getPlayerCountries() {
        return getPlayerCountries(ID);
    }
    
    // helper function to return an array of the countries a player owns in a given list of countries (area)
    // player is the player ID to check; area is the list of countries to search in
    protected int[] getPlayerCountriesInArea(int[] area, int player) {
        // loop through all the countries in area; if player owns them, add them to the results ArrayList
        List<Integer> results = new ArrayList<Integer>();
        for (int i=0; i<area.length; i++) {
            // if the player is us, we need to check for projected ownership (if we own it or if we plan to)
            // if it's not us, just check for ownership the regular way
            if ((player == ID && getProjectedCountryOwner(area[i]) == ID) || countries[area[i]].getOwner() == player) {
                results.add(area[i]);
            }
        }
        
        // Put the country code of each of the countries into an integer array
        int size = results.size();
        int[] intArray = new int[size];
        for(int i=0; i < size; i++) {
            intArray[i] = results.get(i).intValue();
        }
        
        return intArray;
    }
    // overloaded version: if no player is provided, assume it should be us
    protected int[] getPlayerCountriesInArea(int[] area) {
        return getPlayerCountriesInArea(area, ID);
    }

    // helper function to return an array of the countries in a given continent
    protected int[] getCountriesInContinent(int cont) {
        // continent iterator returns all countries in 'cont'
        CountryIterator theCountries = new ContinentIterator(cont, countries);
        
        // Put all the countries into an ArrayList
        ArrayList countryArray = new ArrayList();
        while (theCountries.hasNext()) {
            countryArray.add(theCountries.next());
        }
        
        // Put the country code of each of the countries into an integer array
        int size = countryArray.size();
        int[] intArray = new int[size];
        for(int i=0; i<size; i++) {
            intArray[i] = ((Country)countryArray.get(i)).getCode();
        }
        
        return intArray;
    }
    
    // helper function to return an array of the countries a player does not own in a given continent
    protected int[] getEnemyCountriesInContinent(int owner, int cont) {
        // get all the countries in the continent
        int[] theCountries = getCountriesInContinent(cont);
        // return getEnemyCountriesInArea on the list of countries
        return getEnemyCountriesInArea(theCountries, owner);
    }
    
    // helper function to return an array of the countries a player does not own in a given area
    protected int[] getEnemyCountriesInArea(int[] area, int owner) {
        // add all the countries not owned by owner to a new array list
        ArrayList<Integer> countryList = new ArrayList<Integer>();
        for (int country : area) {
            if (getProjectedCountryOwner(country) != owner) {
                countryList.add(country);
            }
        }
        
        // copy the array list of enemy countries to an integer array
        int size = countryList.size();
        int[] results = new int[size];
        for(int i=0; i<size; i++) {
            results[i] = countryList.get(i);
        }
        
        // return the integer array of enemy countries
        return results;
    }
    // overloaded version; if no owner ID is provided, assume it is us
    protected int[] getEnemyCountriesInArea(int[] area) {
        return getEnemyCountriesInArea(area, ID);
    }
    
    // returns true if <player> owns every country in <area>
    protected boolean playerOwnsArea(int[] area, int player) {
        for (int country : area) {
            if (getProjectedCountryOwner(country) != player) {
                return false;
            }
        }
        return true;
    }
    // overloaded single-parameter version assumes "player" is us
    protected boolean playerOwnsArea(int[] area) {
        return playerOwnsArea(area, ID);
    }
    
    // helper function to return the summary magnitude of all continent bonuses completely contained within <area>
    // we should never have duplicate countries in an area,
    // but note that this function will not work if there are duplicate countries
    protected int getAreaBonuses(int[] area) {
        testChat("getAreaBonuses", "--- Get Area Bonuses ---");
        chatCountryNames("getAreaBonuses", area);
        
        int totalBonus = 0;
        
        // create an array of the number of <area> countries in each continent,
        // where the indices of the list are the continent codes,
        // and the values are the number of <area> countries in that continent
        int[] contPopulations = new int[numConts];
        for (int i=0; i<contPopulations.length; i++) {
            contPopulations[i] = 0; // initially populate the whole list with 0
        }
        for (int country : area) { // loop through area to populate <contPopulations>
            int continent = countries[country].getContinent(); // the continent this country is in
            if (continent >= 0) { // if the country is part of a continent
                contPopulations[continent] += 1; // add 1 to <contPopulations> for this continent
            }
        }
        
        String message = "Countries in continents: \n";
        for (int i=0; i<contPopulations.length; i++) {
            message += board.getContinentName(i) + ": ";
            message += contPopulations[i] + "\n";
        }
        testChat("getAreaBonuses", message);
        
        testChat("getAreaBonuses", "Continents completely covered by area: ");
        
        // now we loop through <contPopulations>, and check each value
        // against the total number of countries that continent contains
        // and if <area> has every country in it, add its bonus to <totalBonus>
        for (int continent=0; continent<contPopulations.length; continent++) {
            int size = BoardHelper.getContinentSize(continent, countries);
            if (contPopulations[continent] == size) { // if <area> has all the countries in this continent
                
                testChat("getAreaBonuses", board.getContinentName(continent));
                
                totalBonus += board.getContinentBonus(continent); // add this continent's bonus to <totalBonus>
            }
        }
        
        testChat("getAreaBonuses", "Total bonus: " + totalBonus);
        
        return totalBonus;
    }
    
    // helper function to return an array of all continent codes completely contained within <area>
    // we should never have duplicate countries in an area,
    // but note that this function will not work if there are duplicate countries
    protected int[] getAreaContinentIDs(int[] area) {
        testChat("getAreaContinentIDs", "--- Get Area Continent ID's ---");
        chatCountryNames("getAreaContinentIDs", area);
        
        ArrayList<Integer> contCodes = new ArrayList<Integer>();
        
        // create an array of the number of <area> countries in each continent,
        // where the indices of the list are the continent codes,
        // and the values are the number of <area> countries in that continent
        int[] contPopulations = new int[numConts];
        for (int i=0; i<contPopulations.length; i++) {
            contPopulations[i] = 0; // initially populate the whole list with 0
        }
        for (int country : area) { // loop through area to populate <contPopulations>
            int continent = countries[country].getContinent(); // the continent this country is in
            if (continent >= 0) { // if the country is part of a continent
                contPopulations[continent] += 1; // add 1 to <contPopulations> for this continent
            }
        }
        
        String message = "Countries in continents: \n";
        for (int i=0; i<contPopulations.length; i++) {
            message += board.getContinentName(i) + ": ";
            message += contPopulations[i] + "\n";
        }
        testChat("getAreaContinentIDs", message);
        
        testChat("getAreaContinentIDs", "Continents completely covered by area: ");
        
        // now we loop through <contPopulations>, and check each value
        // against the total number of countries that continent contains
        // and if <area> has every country in it, add it to <contCodes>
        for (int continent=0; continent<contPopulations.length; continent++) {
            int size = BoardHelper.getContinentSize(continent, countries);
            if (contPopulations[continent] == size) { // if <area> has all the countries in this continent
                
                testChat("getAreaContinentIDs", board.getContinentName(continent));
                
                contCodes.add(continent); // add this continent to <contCodes>
            }
        }
        
        // transfer <contCodes> arraylist to an int[] array to return
        int[] results = new int[contCodes.size()];
        for (int i=0; i<contCodes.size(); i++) {
            results[i] = contCodes.get(i);
        }
        
        return results;
    }

    // when passed an area, will see if it can reduce the number of borders
    // that have to be defended by adding countries to the area (but not removing any)
    // returns a new area with the added countries included
    protected int[] getSmartBordersArea(int[] originalArea) {
        // testing stuff
        String areaName = board.getContinentName(getAreaContinentIDs(originalArea)[0]);
        testChat("getSmartBordersArea", "========== SMART BORDERS FOR " + areaName + " ==========");
        
        // we'll create a number of candidate areas by adding increasing numbers of layers
        // to the outside of the original area and pruning them back
        // (at the end we'll pick the candidate with the smallest number of borders)
        ArrayList<ArrayList<Integer>> candidateAreas = new ArrayList<ArrayList<Integer>>();
        
        // the number of layers to test out to
        // e.g. if the depth is 2, the first candidate layer will simply be the original area
        // the second candidate area will be with 1 layer of countries added and then pruned,
        // and the final candidate area will be with 2 layers of countries added and then pruned
        int depth = 2;
        
        // add the original area to <candidateAreas> as the initial candidate area
        ArrayList<Integer> originalAreaList = new ArrayList<Integer>();
        for (int country : originalArea) { originalAreaList.add((Integer) country); }
        candidateAreas.add(originalAreaList);
        
        // new countries we're adding to the area to be pruned
        // this list will grow with each loop so that the layers are cumulative
        // in other words, the pruning at depth 2, for example, will operate on both the first and second layer
        ArrayList<Integer> addedCountries = new ArrayList<Integer>();
        
        // loop from 1 to <depth>, creating a candidate area of depth <layer> on each loop and adding it to <candidateAreas>
        for (int layer=1; layer<=depth; layer++) {
            testChat("getSmartBordersArea","***** " + layer + " layers out: *****");
            
            // create list of original area plus all the layers we found on previous loops
            // and find its borders so that we can create the new layer
            ArrayList<Integer> lastLayerArea = new ArrayList<Integer>(originalAreaList);
            lastLayerArea.addAll(addedCountries);
            int[] lastLayerBorders = getAreaBorders(lastLayerArea);
            
            // create new layer
            // add all the countries one layer out from each border country to <addedCountries>, ignoring duplicates
            for (int country : lastLayerBorders) { // loop through original borders
                int[] neighbors = BoardHelper.getAttackList(countries[country], countries); // get neighbors of this border country
                for (int neighbor : neighbors) { // loop through each neighbor
                    if (!isInArray(neighbor,originalAreaList) && !isInArray(neighbor, addedCountries)) { // if this neighbor is not in the original area or in any of the new layers (including the one we're creating now—we don't want duplicates)
                        addedCountries.add(neighbor); // add it to <addedCountries>
                    }
                }
            }
            testChat("getSmartBordersArea", "    New layer: " + Arrays.toString(getCountryNames(addedCountries)));
            
            // then we will prune off any countries that are only touching one or fewer non-border countries in the new area;
            candidateAreas.add(pruneAreaAddedCountries(originalAreaList, addedCountries));
            
            // temporary crap for testing purposes
            int oldNumBorders = getAreaBorders(originalArea).length;
            ArrayList<Integer> prunedAdditions = (ArrayList<Integer>) candidateAreas.get(candidateAreas.size()-1).clone();
            int newNumBorders = getAreaBorders(prunedAdditions).length;
            prunedAdditions.removeAll(originalAreaList);
            testChat("getSmartBordersArea", "    Pruned layer: " + (prunedAdditions.size() > 0 ? Arrays.toString(getCountryNames(prunedAdditions)) : "[none]"));
            testChat("getSmartBordersArea","    New borders: " + newNumBorders + ", Original borders: " + oldNumBorders);
        }
        
        // now pick the candidate area with the fewest borders (favoring the lower-layer areas in the case of ties)
        ArrayList<Integer> pickedArea = new ArrayList<Integer>();
        int minBorders = Integer.MAX_VALUE;
        int numOriginalBorders = getAreaBorders(originalArea).length; // number of borders of original area
        int numOriginalCountries = originalArea.length;
        int pickedLayer = -1; // (just for testing purposes)
        String message = "ratios -- "; // just for testing purposes
        for(int i=0; i<candidateAreas.size(); i++) { // loop through all the candidate areas
            int numBorders = getAreaBorders(candidateAreas.get(i)).length; // the number of borders this area has
            if (numBorders < minBorders) { // if this area has the least borders so far
                int numCountries = candidateAreas.get(i).size(); // the number of countries in the proposed new area
                // now we'll check if it's worth adding the number of countries proposed to get the resulting reduction in borders;
                // if the following ratio is less than 1.1, we subjectively deem that it's worth it
                // i.e. if the border reduction is small and the number of countries we have to add is large, it's not worth doing
                // the ratio of the original area is always 1.0, so if all of the proposed new areas fail here, we'll revert to the original
                // (so don't ever change this ratio to less than 1.0, or it will break)
                float ratio = ((float) numBorders / (float) numOriginalBorders) / ((float) numOriginalCountries / (float) numCountries);
                
                message += i + " layer: " + ratio + ", "; // testing purposes
                
                if (ratio < 1.1f) {
                    minBorders = numBorders; // set <minBorders> to this areas number of borders
                    pickedArea = candidateAreas.get(i); // pick it
                    pickedLayer = i; // (just for testing purposes)
                }
            }
        }
        
        testChat("getSmartBordersArea", message);
        testChat("getSmartBordersArea", ">>>>>>>> Picking layer " + pickedLayer + " version");
        testChat("getSmartBordersArea","");
        
        // convert the area we picked to an int[] and return it
        return convertListToIntArray(pickedArea);
    }
    
    // called by getSmartBordersArea()
    // the aforementioned function takes a given area and finds layers of countries around it;
    // we're passed <originalAreaParam>, the original area, and <addedCountriesParam>, a list of all the countries we're adding to it
    // this function adds the new countries to the area and then removes all of those new countries that it can remove
    // without increasing the number of borders of the total area (but doesn't remove any of the original countries)
    // and returns the resulting area as an ArrayList
    protected ArrayList<Integer> pruneAreaAddedCountries(ArrayList<Integer> originalAreaParam, ArrayList<Integer> addedCountriesParam) {
        // first, make a copy of both the lists that we got as parameters so we don't mess up the originals
        ArrayList<Integer> newArea = new ArrayList<Integer>(originalAreaParam);
        ArrayList<Integer> addedCountries = new ArrayList<Integer>(addedCountriesParam);
        
        // add the new countries to the original area to create <newArea>, which we'll then prune down
        newArea.addAll(addedCountries);

        // set some other variables we'll need in our loop
        boolean removed = true; // a flag that tells us if we removed any countries on a given iteration of the while loop
        
        // now we'll loop through <addedCountries> and remove as many countries as we can without increasing the number of borders of <newArea>
        // we'll loop around the whole list as long as we keep removing countries
        while (removed) { // keep looping as long as we removed a country last time
            removed = false; // set <removed> flag to false
            
            // loop through each country in <addedCountries>
            Iterator<Integer> addedCountriesIter = addedCountries.iterator();
            while (addedCountriesIter.hasNext()) {
                int newCountry = addedCountriesIter.next(); // the country we're currently on in the loop
                
                // now test the country we're on
                // if the country itself is a border and if it touches 0 or 1 countries in the new area
                // that aren't themselves borders, then we can remove it without increasing the total number of borders
                if (isAreaBorder(newCountry, newArea)) { // if the country is itself a border
                    int[] neighbors = countries[newCountry].getAdjoiningCodeList(); // get neighbors
                    int numInteriorNeighbors = 0; // the number of interior neighbors (countries that <country> can attack that are in the area but are not borders of the area)
                    for (int neighbor : neighbors) { // loop through all neighbors
                        if (countries[newCountry].canGoto(neighbor) && isInArray(neighbor,newArea) && !isAreaBorder(neighbor, newArea)) { // if <country> can attack <neighbor> and neighbor is in <newArea> and <neighbor> is not a border
                            numInteriorNeighbors += 1;
                        }
                    }
                    if (numInteriorNeighbors <= 1) { // if the country is touching no more than 1 interior country
                        // then we don't want to use it as a smart border, so
                        addedCountriesIter.remove(); // remove it from <addedCountries>
                        newArea.remove((Integer) newCountry); // and also from <newArea>
                        removed = true; // set <removed> flag to true so we will loop around the whole list again
                    }
                }
            }
        }
        
        // return the pruned area
        return newArea;
    }
    
    // given a continent, get an array of continents that neighbor it and can attack it
    protected int[] getNeighboringContinents(int cont) {
        ArrayList<Integer> neighborConts = new ArrayList<Integer>();
        int[] neighbors = BoardHelper.getDefensibleBordersBeyond(cont, countries);
        for (int neighbor : neighbors) {
            int contCode = countries[neighbor].getContinent();
            if (!isInArray(contCode, neighborConts) && contCode != cont) {
                neighborConts.add(contCode);
            }
        }
        return convertListToIntArray(neighborConts);
    }
    
    // chat out all continent codes and names
    protected void chatContinentNames() {
        for (int i=0; i<numConts; i++) {
            board.sendChat(i + " - " + board.getContinentName(i));
        }
    }
    
    // return the largest continent bonus of all continents on the board
    protected int getBiggestContinentBonus() {
        int biggestBonus = 0;
        for (int i=0; i<numConts; i++) {
            int bonus = board.getContinentBonus(i);
            if (bonus > biggestBonus) {
                biggestBonus = bonus;
            }
        }
        return biggestBonus;
    }
    
    // checks if an integer is in an integer array
    protected boolean isInArray(int test, int[] array) {
        for (int element : array) {
            if (element == test) {
                return true;
            }
        }
        return false;
    }
    protected boolean isInArray(int test, ArrayList<Integer> list) {
        int size = list.size();
        for (int i=0; i<size; i++) {
            if (list.get(i) == test) {
                return true;
            }
        }
        return false;
    }
    
    // get "projected" owner of the given country
    // returns <ID> if we actually own the country or if the country is in the battlPlan arraylist
    // because in that second case, that means we're planning on taking it over this turn
    protected int getProjectedCountryOwner(int country) {
        int currentOwner = countries[country].getOwner();
        if (isInBattlePlan(country)) {
            return ID;
        }
        return currentOwner;
    }
    
    // get "projected" armies on the given country
    // returns the actual armies on the country, unless we're planning on taking it over this turn
    // in which case it returns the number of armies we expect to leave on it after taking it over
    protected int getProjectedArmies(int country) {
        int armies = 0;
        if (isInBattlePlan(country)) {
            armies = checkBorderStrength(country) + 1;
        } else {
            armies = countries[country].getArmies();
        }
        return armies;
    }
    
    // checks if a given country is in the battlePlan arraylist
    protected boolean isInBattlePlan(int country) {
        for (int[] path : battlePlan) {
            for (int checkCountry : path) {
                if (checkCountry == country) {
                    return true;
                }
            }
        }
        return false;
    }
    
    // returns true if any country in the given area is contained within <battlePlan>
    protected boolean battlePlanHasCountryIn(int[] area) {
        for (int country : area) {
            if (isInBattlePlan(country)) {
                return true;
            }
        }
        return false;
    }
    
    // return the total income of all enemies remaining in the game
    protected int getTotalEnemyIncome() {
        int numberOfPlayers = board.getNumberOfPlayers(); // number of players that started the game
        int totalEnemyIncome = 0;
        for (int player=0; player<numberOfPlayers; player++) { // loop through all players
            if (BoardHelper.playerIsStillInTheGame(player, countries) && player != ID) { // if the player is still in the game, and isn't us
                totalEnemyIncome += board.getPlayerIncome(player); // add its income to totalEnemyIncome
            }
        }
        return totalEnemyIncome;
    }
    
    // returns the income of the given player including their potential income from cars
    // (the value of the next card set * 1/3 the number of cards they have)
    protected int getPlayerIncomeAndCards(int player) {
        int income = board.getPlayerIncome(player); // the player's actual income
        income += Math.ceil((double) board.getPlayerCards(player) / 3.0d * (double) board.getNextCardSetValue());  // add the value of their cards, rounded up (each card is treated as 1/3 the value of the next card set)
        return income;
    }
    
    // converts an arraylist of integers into an array of integers
    protected int[] convertListToIntArray(ArrayList<Integer> list) {
        int size = list.size();
        int[] array = new int[size];
        for (int i=0; i<size; i++) {
            array[i] = list.get(i);
        }
        return array;
    }
    // overloaded version to allow us to pass a set in place of a list
    protected int[] convertListToIntArray(Set<Integer> set) {
        ArrayList<Integer> list = new ArrayList<Integer>(set);
        return convertListToIntArray(list);
    }
    
    // returns the key that contains the lowest value in a HashMap<Integer, Integer>
    protected Integer keyWithSmallestValue(HashMap<Integer, Integer> map) {
        if (map.isEmpty()) {
            return null;
        }
        
        ArrayList<Integer> keys = new ArrayList<Integer>(map.keySet());
        int smallestValKey = 0;
        int smallestValue = keys.get(0);
        for (int key : keys) {
            int value = map.get(key);
            if (value < smallestValue) {
                smallestValue = value;
                smallestValKey = key;
            }
        }
        
        return smallestValKey;
    }
}
