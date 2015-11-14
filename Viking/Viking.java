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
    
    // will store the number of armies we want to put on a border country
    // key: country code
    // value: number of armies
    protected Map<Integer, Integer> borderArmies;
    
    public Viking()
    {
        rand = new Random();
        battlePlan = new ArrayList<int[]>();
        borderArmies = new HashMap<Integer, Integer>();
    }
    
    // Save references
    public void setPrefs( int newID, Board theboard )
    {
        ID = newID;		// this is how we distinguish what countries we own
        
        board = theboard;
        countries = board.getCountries();
        numConts = board.getNumberOfContinents();

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
//            "attackPhase",
//            "moveArmiesIn",
            
//            "continentFitness",
            "getAreaTakeoverPaths",
//            "findAreaPaths",
            "pickBestTakeoverPaths",
//            "getCheapestRouteToArea",
//            "placeArmiesOnRoutes",
//            "calculateCladeCost",
            "calculateBorderStrength",
            "nothing"
        };
        
        for (int i=0; i<topics.length; i++) {
            if (topic == topics[i]) {
                board.sendChat(message);
            }
        }
    }
    
    // pick initial countries at the beginning of the game. We will do this later.
    public int pickCountry()
    {
        return -1;
    }
    
    // place initial armies at the beginning of the game
    public void placeInitialArmies( int numberOfArmies ) {
        testChat("placeInitialArmies", "*********** PLACE INITIAL ARMIES ***********");
        
//        chatContinentNames();

        // do exactly as we do at the beginning of a normal turn
        placeArmies(numberOfArmies);
    }
    
    public void cardsPhase( Card[] cards ) {
    }
    
    // place armies at the beginning of each turn
    public void placeArmies( int numberOfArmies ) {
        testChat("placeArmies", "*********** PLACE ARMIES ***********");
        
        // empty battlePlan of any info from previous turn
        battlePlan.clear();
        
        // find list of objectives to knockout enemy bonuses
        ArrayList<HashMap> knockoutObjectiveList = findKnockoutObjectives();
        
        // sort the knockout objectives first by the bonus of the continent to knock out
        // and then by enemy income, so we can knockout the biggest bonuses by the strongest enemy
        sortKnockoutObjectives(knockoutObjectiveList, "bonus");
        sortKnockoutObjectives(knockoutObjectiveList, "enemyIncome");
        
        testChat("placeArmies", "--- Sorted Knockout Objectives: --- Size: " + knockoutObjectiveList.size());
        chatObjectives("placeArmies", knockoutObjectiveList);
        
        // for now, just add the first two to the battlePlan, trimming them for collisions
        int max = Math.min(2, knockoutObjectiveList.size());
        for (int i=0; i<max; i++) {
            HashMap objective = knockoutObjectiveList.get(i);
            ArrayList<int[]> objectivePlan = (ArrayList<int[]>) objective.get("plan");
            int[] objectivePath = objectivePlan.get(0);
            battlePlan.add(trimFork(battlePlan, objectivePath));
        }
        
        ArrayList<HashMap> takeoverObjectiveList = findTakeoverObjectives();
        testChat("placeArmies", "--- Takeover Objectives: --- ");
        chatObjectives("placeArmies", takeoverObjectiveList);
        
        // first we'll decide what continents to pursue and thus which ones we'll place armies on
        int[] bestContList = rateContinents(); // get ordered list of best continents to pursue
//        bestContList = new int[] { 3,14,5 }; // interesting continents in U.S.S. Lionhart map
        
        // next, loop through the continents from best to worst
        // adding just enough armies to each continent to take it over completely
        // until we're out of armies to place
        for (int i=0; i<bestContList.length; i++) {
            // first check if we're out of armies, and if we are, stop trying to place them
            if (numberOfArmies <= 0) {
                break;
            }
            
            // if we don't already own the continent, place armies on it
            //if (BoardHelper.playerOwnsContinent(ID, bestContList[i], countries) == false) {
                int goalCont = bestContList[i];
            
                testChat("placeArmies","***** Goal Continent: " + board.getContinentName(goalCont) + " *****");
                
                // next we need to pick the right countries to place our armies on in order to take over the continent
                // the pickBestTakeoverPaths function will simulate taking over the continent
                // from multiple countries and give us back the best set of paths to do so
                // the first country in each path (or route) is the one we want to place our armies on
                int[] goalContCountries = getCountriesInContinent(goalCont); // put all the countries from the goal cont into an integer array to pass to the getAreaTakeoverPaths function
                ArrayList<int[]> thisContPlan = pickBestTakeoverPaths(goalContCountries); // get the best paths to take over the goalCont
                
                // now check all the paths in our new continent against all the paths already in battlePlan (from other continents)
                // to see if they collide; if they do, trim the new path so that it will be treated like a fork
                // this will only happen if there was an earlier continent that we didn't own any countries in,
                // which we had to get to first, in which case, some countries from THIS continent might already be in some paths in battlePlan
                // there are some cases that this fix does not solve, but they should be reasonably rare
                for (int j=0; j<thisContPlan.size(); j++) {
                    int[] trimmedPath = trimFork(battlePlan, thisContPlan.get(j));
                    battlePlan.add(trimmedPath);
                }
            
                setBorderStrength(goalContCountries);
                
                // place only the number of armies needed to takeover the continent on the starting countries of all the paths
                // store any remaining armies available in numberOfArmies
                numberOfArmies = placeArmiesOnRoutes(battlePlan,numberOfArmies);
                
                testChat("placeArmies", "Number of armies left after placing on continent: " + numberOfArmies);
            //}
        }
        
//        testChat("placeArmies", "Paths we picked: ");
//        chatCountryNames("placeArmies", battlePlan);
        
//        testChat("placeArmies", "Border countries for all continents:");
//        ArrayList<Integer> borderCountries = new ArrayList<Integer>();
//        for (int key : borderArmies.keySet()) {
//            borderCountries.add(key);
//        }
//        chatCountryNames("placeArmies",borderCountries);
    }
    
    // attack!
    public void attackPhase() {
        testChat("attackPhase", "*********** ATTACK PHASE ***********");
        
        // loop through battlePlan (calculated in the placeArmies() phase),
        // which contains multiple attack routes, and execute each one
        for (int i=0; i<battlePlan.size(); i++) {
            testChat("attackPhase", "------- Attack route: -------");
            chatCountryNames("attackPhase", battlePlan.get(i));
            
            if (countries[battlePlan.get(i)[0]].getOwner() == ID) { // if we own the first country in the path
                int[] attackRoute = battlePlan.get(i);
                
                testChat("attackPhase", "First country on route has " + countries[attackRoute[0]].getArmies() + " armies.");
                
                // loop through the whole route, attacking as we go
                for(int j=0; j<attackRoute.length-1; j++) {
                    
                    testChat("attackPhase", "Calculating forks from this country...");
                    
                    // at each step of the path, before we actually attack
                    // we test for forks. if we find a branch point from this country
                    // then we have to tell moveArmiesIn() to leave some armies behind
                    // in order to take over the fork later from this point
                    int forkArmies = 0; // how many armies we want to leave behind to use for any forks from this country
                    for (int k=i+1; k<battlePlan.size(); k++) { // loop through only the rest of the battlePlan paths (i.e. the ones we haven't attacked yet) to check for branch points
                        if (attackRoute[j] == battlePlan.get(k)[0]) {
                            forkArmies += calculateCladeCost(battlePlan, k); // calculate cost of any clades that fork from this point, and add them all to forkArmies
                        }
                    }
                    
                    if (forkArmies == 0) { testChat("attackPhase", "No forks from this country"); }
                    
                    // find out if we want to leave any armies on this country as a border garrison
                    int garrisonArmies = checkBorderStrength(attackRoute[j]);
                    
                    // leaveArmies is a global variable
                    // this is how many armies we want to leave on the attacking country
                    // both for forking from that country and to leave there as a border garrison
                    // moveArmiesIn() will use this variable to do that
                    leaveArmies = forkArmies + garrisonArmies;
                    
                    // now we attack
                    if (countries[attackRoute[j]].getArmies() > 1) { // if we have > 1 army in the attacking country
                        board.attack(attackRoute[j],attackRoute[j+1],true); // attack the next country in the route
                    } else {
                        break;
                    }
                }
            } else {
                testChat("attackPhase", "We do not own the starting country of this route");
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
    
    public void fortifyPhase()
    {
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
        
        // loop through all the continents to create an "objective" hashmap for each one and add it to objectiveList
        for(int continent = 0; continent < numConts; continent++) {
            HashMap<String, Object> objective = new HashMap<String, Object>(); // the Objective hashmap for this continent
            
            // set continent code
            objective.put("continentID", continent);
            
            // find and set area
            // in the future this may include some extra countries outside the continent in order to
            // reduce the number of borders necessary to defend, but for now the area will just be the continent proper
            int[] area = getCountriesInContinent(continent);
            objective.put("area", area);
            
            // set continent bonus
            objective.put("bonus", board.getContinentBonus(continent));
            
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
            if (entryPath.length > 2) { // if entryPath is larger than two countries, then we have a non-trivial path to the area (meaning we own no countries in the area), which we need to account for in the cost
                int[] pathAndArea = new int[entryPath.length - 2 + enemyCountries.length]; // new array to hold the path and the countries in the area
                System.arraycopy(entryPath,1,pathAndArea,0,entryPath.length-2); // copy entryPath into new array, except the first element (which is a country we own, so doesn't count toward cost) and the last element (which is a country in the area, so it's already in enemyCountries)
                System.arraycopy(enemyCountries,0,pathAndArea,entryPath.length-2,enemyCountries.length); // copy the countries in the area into the new array
                cost = getGlobCost(pathAndArea); // estimate the cost of the area and the path together
            } else { // in case the entry path is smaller than two countries, we don't need it, because we own at least one country in the area or an adjacent country
                cost = getGlobCost(enemyCountries); // so in this case, get the cost of just the enemy countries in the area
            }
            for (int country : area) { // now loop through every country in the area to add border garrisons to the cost
                int borderStrength = calculateBorderStrength(country, area, numBorders); // what the border garrison should be for this country; if it is not a border, this will be 0
                int borderArmies = countries[country].getOwner() == ID ? countries[country].getArmies() - 1 : 0; // how many (extra) armies are on this country if we own it; if we don't own it, 0
                cost += Math.max(0,borderStrength - borderArmies); // add the border strength we want to the cost minus any armies we already have on that border (or 0 if there are more than enough armies already there)
            }
            objective.put("cost", cost);
            
            // add objective to objectiveList arraylist
            objectiveList.add(objective);
        }
        
        return objectiveList;
    }
    
    // sort in place an arraylist of knockout objectives
    // by the value of sortKey (only if that value is an integer)
    protected void sortKnockoutObjectives(ArrayList<HashMap> list, String sortKey) {
        // if there's nothing in the list, or if the value we're trying to sort by is not an integer
        // simply return the original list
        if (list.size() == 0 || !(list.get(0).get(sortKey) instanceof Integer)) {
            return;
        }
        
        // bubble-sort the arraylist by the value of sortKey
        boolean flag = true;
        HashMap temp = new HashMap();
        int v1, v2;
        int size = list.size();
        while(flag) {
            flag = false;
            for (int i=0; i<size-1; i++) {
                v1 = (Integer) list.get(i).get(sortKey);
                v2 = (Integer) list.get(i+1).get(sortKey);
                if (v1 < v2) {
                    temp = list.get(i); // store the value at i
                    list.remove(i); // remove the ith element, and everything after it shifts to the left
                    list.add(i+1,temp); // insert the original ith element at i+1, and everything after it shifts to the right
                    flag = true;
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
            int owner = countries[BoardHelper.getCountryInContinent(continent, countries)].getOwner(); // the owner of some country in this continent
            if (BoardHelper.anyPlayerOwnsContinent(continent, countries) && owner != ID) { // if an enemy fully owns this continent
                HashMap<String, Object> objective = new HashMap<String, Object>(); // the Objective hashmap for this continent
                
                // set continent code
                objective.put("continentID", continent);
                
                // set continent bonus
                objective.put("bonus", board.getContinentBonus(continent));
                
                // set enemy income
                objective.put("enemyIncome", board.getPlayerIncome(owner));
                
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
                
                
                
                objectiveList.add(objective);
            }
        }
        
        
        return objectiveList;
    }
    
    
    // checks if country is in borderArmies hashmap, and if it is, returns the value, if not, returns 0
    // i.e. if the country is a border, this function will return the number of armies we intend to put/leave on it as a garrison
    protected int checkBorderStrength(int country) {
        if (borderArmies.get(country) != null) {
            return borderArmies.get(country);
        }
        return 0;
    }

    // called by placeArmies(), figures out how many armies to put on each border country of the given area
    // stores the number of armies it calculates for each country in the global hashmap borderArmies
    // does NOT actually place those armies on the countries
    protected void setBorderStrength(int[] area) {
        int numBorders = getAreaBorders(area).length;
        for (int country : area) {
            int strength = calculateBorderStrength(country, area, numBorders);
            borderArmies.put(country, strength); // borderArmies is a global hashmap
        }
    }
    
    // calculate how many armies to leave on the given country as a border garrison
    // if the country is not a border, will return 0;
    // later this will be smarter, but for now, we're just putting 5 on all the borders
    //
    // WE HAVE NOT TESTED THIS FUNCTION YET
    protected int calculateBorderStrength(int country, int[] area, int numBorders) {
        int strength = 0;
        if (isAreaBorder(country, area)) { // if <country> is a border of <area>
            
            testChat("calculateBorderStrength", "--- calculateBorderStrength of: " + countries[country].getName() + " ---");
            
            int income = board.getPlayerIncome(ID); // our income
            int greatestThreat = findGreatestThreat(country); // highest value of extant armies + player income among nearby countries
            int biggestBonus = getBiggestContinentBonus();
            int bonus = getAreaBonuses(area); // set this to the bonus of all continents completely contained by <area>
            double areaValue  = Math.pow(bonus, 0.5)/Math.pow(biggestBonus, 0.5); // relative value of this bonus compared to highest bonus
            
            // formula to set <strength> based on the relevant factors
            // sets <strength> to the biggest nearby threat, scaled down by the relative value of the bonus we're protecting
            // for a small bonus will have an insufficient border, and the largest bonus on the board
            // will have a bonus equal to <greatestThreat>
            // except if <income>/<numBorders> is smaller than that number, we limit <strength> to that
            // in order to keep the border garrison requirements from being out of control
            strength = (int) Math.ceil(Math.min(greatestThreat * areaValue, (double) income / (double) numBorders));
        }
        
        
        return 5;
    }
    
    // find the strength of the greatest nearby threat to a given country;
    // this is used to help determine how strong a given border should be
    //
    // search all neighbors out from the given country <toCountry> to a certain depth;
    // for each country in that neighborhood (that we don't own)
    // adding together its armies and the income of the player that owns it
    // return the highest such sum that we find
    protected int findGreatestThreat(int toCountry) {
        int depth = 3; // the depth out to which we want to search
        int greatestThreat = 0; // will contain the magnitude of the greatest threat, which we will return
        
        ArrayList<Integer> neighborhood = new ArrayList<Integer>(); // will contain all the countries within <depth> from <country>
        neighborhood.add(toCountry); // initially add the country we're searching from
        int stop = 0; // set initial stop marker for neighborhood to 0
        
        // first we find all the nearby countries (within <depth> from starting country)
        int start = 0; // the index value of <neighborhood> where start the countries whose neighbors we haven't found yet on each <i> loop; initial value should be 0, to start at the beginning
        for (int i=0; i<depth; i++) { // depth loop
            // set the start point for the <j> loop to the old stop point, i.e. the previous end of <neighborhood> before we added the latest neighbors on the last loop
            // so that the <j> loop will start after the countries whose neighbors we've already found,
            // only looping over the neighbors of the countries we added on the previous loop
            start = stop;
            stop = neighborhood.size(); // set new stop point for <j> loop to the end of <neighborhood>
            for (int j=start; j<stop; j++) { // loop through all the countries we haven't checked for neighbors yet (breadth loop)
                // find the neighbors of this country
                int[] neighbors = BoardHelper.getAttackList(countries[neighborhood.get(j)], countries);
                
                // loop through the neighbors of this country and add all the ones we haven't already seen
                for (int neighbor : neighbors) {
                    if (!isInArray(neighbor, neighborhood)) {
                        neighborhood.add(neighbor);
                    }
                }
            }
        }
        
        testChat("calculateBorderStrength", "There are " + (neighborhood.size() - 1) + " countries " + depth + " deep from " + countries[toCountry].getName() + ":");
        chatCountryNames("calculateBorderStrength", neighborhood);
        
        // loop through <neighborhood> and find greatest threat
        neighborhood.remove(0); // delete <toCountry> from the list of countries to check
        for (int country : neighborhood) {
            int owner = countries[country].getOwner();
            if (owner != ID) { // if we don't own the country
                // the threat of this country is the number of armies on the country plus the income of the player that owns it;
                // later we may want to include an assessment of that player's cards here as well
                // and also how many countries/armies are between there and <toCountry>
                int threat = countries[country].getArmies() + board.getPlayerIncome(owner);
                // if this country is the greatest threat so far, set that as our <greatestThreat>
                if (threat > greatestThreat) {
                    greatestThreat = threat;
                }
            }
        }
        
        testChat("calculateBorderStrength","greatestThreat to " + countries[toCountry].getName() + ": " + greatestThreat);
        
        return greatestThreat;
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

        // return the number of armies we have left
        return numberOfArmies;
    }
    
    // calculates the cost of taking over a clade
    // a clade is a monophyletic tree of paths, i.e. a path and all of its forks (and all of their forks, etc.)
    // clades are not represented in our code as single objects, so this function must find the forks of the initial path itself
    // the function is passed an entire takeover plan ("plan"), which may contain multiple clades, and an index, which tells it which original path in the plan to work on
    // it follows that original path only, and finds all of its forks, and calls itself recursively to find all of its forks' forks, etc.
    // and adds up all of their costs together and returns that number
    protected int calculateCladeCost(ArrayList<int[]> plan, int index) {
        int cost = 0;
        int[] path = plan.get(index);
        cost += getPathCostWithBorders(path); // calculate the cost of the main path
        //remove plan[index]
        
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
        float cost = 0;
        for (int i=1; i<path.length; i++) { // loop through the path, beginning on the SECOND country
            cost += countries[path[i]].getArmies(); // add enemy armies to the cost
        }
        
        // here comes the subjective part
        // cost so far contains just the number of enemy armies
        // but this number isn't enough to ensure a successful takeover, so we will add a buffer
        cost *= 1.1; // add a buffer of 10% of the enemy armies
        cost += path.length - 1; // add 1 additional army for each country in the path, since we always have to leave 1 behind as we attack
        
        return (int) Math.ceil(cost); // round UP to the nearest integer and return
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
//        testChat("getAreaTakeoverPaths", "-- GET AREA TAKEOVER PATHS --");
//        testChat("getAreaTakeoverPaths", "startCountry not given");
        
        // we'll store all candidate paths here (individual paths are integer arrays)
        ArrayList<int[]> paths = new ArrayList<int[]>();

        // we'll test every path from every country we own in the countryList
        // if we don't own any countries in countryList, we'll find a country close-by to start from
        
        int[] candidates = getPlayerCountriesInArea(countryList); // get countries in countryList that we own

        if (candidates.length > 0) { // if we own any countries in countryList
            
            // just testing to see if we found the countries we own
//            String[] countryNames = getCountryNames(candidates);
//            testChat("getAreaTakeoverPaths", "countries we own in goalCont: " + Arrays.toString(countryNames));
            
            // loop through candidates array, finding paths for each of them
            int[] initialPath = new int[1];
            for (int i=0; i<candidates.length; i++) {
                initialPath[0] = candidates[i];
                paths.addAll(findAreaPaths(initialPath, countryList)); // concatenate results from all of them together in the paths ArrayList
            }
        }
        else { // we don't own any countries in countryList
//            testChat("getAreaTakeoverPaths", "we don't own any countries in goalCont");
            
            // find the cheapest path to it from some country we own
            // we pass 'false' as the second parameter to tell the function that we don't care
            // how many armies are on the border of the area that we end up at; we only care about
            // the cost of the path up until that point, since we're planning on taking over the whole area anyway
            int[] initialPath = getCheapestRouteToArea(countryList, false);
            
//            String[] countryNames = getCountryNames(initialPath);
//            testChat("getAreaTakeoverPaths", "Path to continent: " + Arrays.toString(countryNames));
            
            // use that as starting country
            paths = findAreaPaths(initialPath, countryList);
        }
        
        // now we have a complete list of terminal paths from every country we own in the area (countryList)
        // (or from a nearby country in the case that we don't own any in the area itself)
        // but we still need to go over all of the countries we have paths to, and see if any are left out.
        // this will happen in the case of non-contigious areas, i.e. "you can't get there from here"
        // so we'll check if any of the countries in countryList are not in any of the paths we found
        // and repackage just them as a new area, call getAreaTakeoverPaths() recursively on that area
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
        // (which should only happen if the original area we searched was not contiguous and we didn't own a country in at least one of the discrete parts)
        // so convert it into an integer array and pass it into getAreaTakeoverPaths() recursively as a new area to search
        // then we'll add the results of that function call to the paths we already found
        if (countriesLeft.size() > 0) {
            int[] countriesLeftArray = new int[countriesLeft.size()];
            for (int i=0; i<countriesLeftArray.length; i++) {
                countriesLeftArray[i] = countriesLeft.get(i).intValue();
            }
//            String[] countryNames = getCountryNames(countriesLeftArray);
//            testChat("getAreaTakeoverPaths", "countriesLeft: " + Arrays.toString(countryNames));
            
            paths.addAll(getAreaTakeoverPaths(countriesLeftArray));
            
//        } else {
//            testChat("getAreaTakeoverPaths", "all countries were accounted for in the list of paths we found");
        }
        
        // now we will add single-country paths for each country we own
        // these aren't terminal paths, of course; they're kind of dummy paths
        // they will be added to the battlePlan, from which the border countries among them will be armed
        // by treating the border countries as paths, we harness a lot of existing logic for placing armies
        for (int country : countryList) {
            if (countries[country].getOwner() == ID) {
                paths.add(new int[]{country});
            }
        }
        
        testChat("getAreaTakeoverPaths", "There are " + paths.size() + " terminal paths");
        
        // choose and return the best paths
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
        
        // choose and return the best paths
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
            System.out.println("ERROR in cheapestRouteFromOwnerToCont() -> bad parameters");
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
            
            if ( countries[testCode].getOwner() == owner ) {
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
                                              armiesSoFar + (countries[canAttackInto[i]].getOwner() == owner ? -countries[canAttackInto[i]].getArmies() : countries[canAttackInto[i]].getArmies()),
                                              newHistory );
                    haveSeenAlready[ countries[canAttackInto[i]].getCode() ] = true;
                }
            }
            
            // as far as we know, this should only happen in maps with one-way connections
            // if the only country owned by owner is trapped behind a one-way connection from the area
            if (Q.isEmpty()) {
                System.out.println("ERROR in cheapestRouteFromOwnerToCont->can't pop");
                return null;
            }
        }
        // End of cheapestRouteFromOwnerToCont
    }
    // overload getCheapestRouteToArea to allow a single parameter version
    // if no ID is provided, assume it should be the owner
    protected int[] getCheapestRouteToArea(int[] area, boolean into) {
        return getCheapestRouteToArea(area, into, ID);
    }
    
    // called in the placeArmies() and placeInitialArmies() phases
    // given that the bot has already chosen a continent (or abstract area) to pursue, this function should find a comprehensive plan
    // to takeover that continent/area in the form of a set of int[] arrays of country codes that form attack paths from countries we own
    // this function calls getAreaTakeoverPaths() to get a list of all possible takeover paths (allPaths) through a given country list (area)
    // then finds a comprehensive set of paths that pass through every enemy country in the area, including forks and islands
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

//            testChat("pickBestTakeoverPaths", "-- Paths we're picking:");
//            chatCountryNames("pickBestTakeoverPaths", results);
            
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

//            testChat("pickBestTakeoverPaths", "-- Pruned list of paths:");
//            chatCountryNames("pickBestTakeoverPaths", checkPaths);

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
            
//            testChat("pickBestTakeoverPaths", "-- Pruned version of countriesLeft:");
//            if (countriesLeft.size() > 0) {
//                chatCountryNames("pickBestTakeoverPaths", countriesLeft);
//            } else {
//                testChat("pickBestTakeoverPaths", "[] - no countries in countriesLeft");
//            }
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
    
    // checks to see if country is a border of area by seeing if any of its
    // neighbors is outside of area
    protected boolean isAreaBorder (int country, int[] area) {
        int[] neighbors = countries[country].getAdjoiningCodeList(); // get neighbors
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
            
            terminalPaths.add(historyCopy);
        }
        
        // return the terminalPaths arrayList. if we're at the end of a path, this will contain
        // a single terminal path. If we're not at the end of a path, it will contain all the terminal
        // paths below us that were found recursively, which will then be concatenated with any other
        // terminal paths that were found elsewhere (i.e. the branches that split above us) as they bubble up
        return terminalPaths;
    }
    
    // called by the findAreaPaths function to determine whether a potential country in a path is valid
    // i.e. we don't own it, it hasn't been visited already, and it's in the specified list of countries (e.g. a certain continent)
    protected boolean pathNeighborIsValid(int neighbor, int[] history, int[] countryList) {

        // first check if we own the country
        if (countries[neighbor].getOwner() == ID) {
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
    
    // function to rate continents to pursue based on several factors,
    // such as size, bonus, number of countries we own, etc...
    protected int[] rateContinents() {
        
        int goalCont = -1; // the continent we will choose
        float bestFitness = 0; // the continent with the best fitness so far
        float fitness; // the fitness of the current continent in each loop
        int numConts = BoardHelper.numberOfContinents(countries); // number of continents
        // declare the factors for each continent from which we'll calculate the fitness
        float bonus, numCountriesOwned, numArmiesOwned, numCountries, numEnemyArmies, numBorders;
        
        Map fitnessMap = new HashMap(); // key value array to store continent ID along with its fitness
        int[] results = new int[numConts]; // array of continent ID's, which will later be sorted by fitness
        String message = "";
        String name;
        
        // loop through all the continents and calculate their fitness
        for(int cont = 0; cont < numConts; cont++) {
            
            // get the factors for the continent to calculate the fitness
            bonus = board.getContinentBonus(cont); // bonus
            numCountriesOwned = getPlayerCountriesInContinent(cont).length; // how many countries we own in cont
            numArmiesOwned = BoardHelper.getPlayerArmiesInContinent(ID, cont, countries); // how many of our armies in cont
            numCountries = BoardHelper.getContinentSize(cont, countries); // how many countries in cont
            numEnemyArmies = BoardHelper.getEnemyArmiesInContinent(ID, cont, countries); // how many enemy armies in cont
            numBorders = getSmartContinentBorders(cont, countries).length; // how many border countries
            name = board.getContinentName(cont);
            
            // calculate fitness
            fitness = (bonus * (numCountriesOwned + 1) * (numArmiesOwned + 1)) / ((float) Math.pow(numCountries,1) * (float) Math.pow(numBorders,1) * (numEnemyArmies + 1));
//            fitness = numCountries; // pick biggest continent for testing purposes
            
            fitnessMap.put(cont,fitness); // store fitness and ID as a key value pair in the map
            results[cont] = cont; // store continent ID's in this array, will get sorted later
            
            message = message + "\nname: " + name + " ID: " + cont + " fitness: " + fitnessMap.get(cont);
        }
        
        testChat("continentFitness",message);
        
        // bubble sort the results array by fitness
        boolean flag = true;
        int temp;
        float v1, v2;
        while(flag) {
            flag = false;
            for (int i=0; i<results.length-1; i++) {
                v1 = (Float)fitnessMap.get(results[i]);
                v2 = (Float)fitnessMap.get(results[i+1]);
                if (v1 < v2) {
                    temp = results[i];
                    results[i] = results[i+1];
                    results[i+1] = temp;
                    flag = true;
                }
            }
        }
        
        testChat("continentFitness",Arrays.toString(results));
        
        return results;
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
    
    // a custom comparator class to compare integer arrays by length
    // NOT WORKING AT THE MOMENT
/*    class intArrayLengthComp implements Comparator<int[]> {
        public int compare(int[] a, int[] b) {
            if (a.length > b.length) {
                return 1;
            } else if (a.length < b.length) {
                return -1;
            } else {
                return 0;
            }
        }
    }*/
    
    // takes an integer array of country codes and returns a string array of the associated country names
    // useful for testing purposes
    protected String[] getCountryNames(int[] codes) {
        int size = codes.length;
        String[] names = new String[size];
        for (int i=0; i<size; i++) {
            names[i] = countries[codes[i]].getName().replace(",",""); // get rid of commas in country names because that's confusing when we output the whole array as a string
        }
        return names;
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
        if (value instanceof Integer) {
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
        
        return null;
    }
    
    // helper function to return an array of the countries a player owns in a given continent
    // player is the player ID to check; cont is the continent in question
    protected int[] getPlayerCountriesInContinent(int cont, int player) {
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
    }

    
    // helper function to return an array of the countries a player owns in a given list of countries (area)
    // player is the player ID to check; area is the list of countries to search in
    protected int[] getPlayerCountriesInArea(int[] area, int player) {
        // loop through all the countries in area; if player owns them, add them to the results ArrayList
        List<Integer> results = new ArrayList<Integer>();
        for (int i=0; i<area.length; i++) {
            if (countries[area[i]].getOwner() == player) {
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
    protected int[] getEnemyCountriesInContinent(int owner, int cont, Country[] countries) {
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
            if (countries[country].getOwner() != owner) {
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
    
    // helper function to return the summary magnitude of all continent bonuses completely contained within <area>
    //
    // THIS FUNCTION HAS NOT BEEN TESTED YET!
    protected int getAreaBonuses(int[] area) {
        int totalBonus = 0;
        
        // create an ArrayList of the number of <area> countries in each continent,
        // where the indices of the list are the continent codes,
        // and the values are the number of <area> countries in that continent
        ArrayList<Integer> contPopulations = new ArrayList<Integer>();
        for (int i=0; i<numConts; i++) {
            contPopulations.add(0); // initially populate the whole list with 0
        }
        for (int country : area) { // loop through area to populate <contPopulations>
            int continent = countries[country].getContinent(); // the continent this country is in
            if (continent > 0) { // if the country is part of a continent
                contPopulations.set(continent,contPopulations.get(continent) + 1); // add 1 to <contPopulations> for this continent
            }
        }
        
        // now we loop through <contPopulations>, and check each value
        // against the total number of countries that continent contains
        // and if <area> has every country in it, add its bonus to <totalBonus>
        for (int continent : contPopulations) {
            int size = BoardHelper.getContinentSize(continent, countries);
            if (contPopulations.get(continent) == size) { // if <area> has all the countries in this continent
                totalBonus += board.getContinentBonus(continent); // add this continent's bonus to <totalBonus>
            }
        }
        
        return totalBonus;
    }

    // custom get continent borders function
    protected int[] getSmartContinentBorders(int cont, Country[] countries) {
        // eventually this function will pick borders of the continent that may include countries outside of the continent itself such that the number of borders to defend is smaller.
        // For now, it will just call the regular BoardHelper function to get the actual borders
        return BoardHelper.getContinentBorders(cont, countries);
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
}
