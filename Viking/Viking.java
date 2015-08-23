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
    
    // It might be useful to have a random number generator
    protected Random rand;
    
    public Viking()
    {
        rand = new Random();
    }
    
    // Save references
    public void setPrefs( int newID, Board theboard )
    {
        ID = newID;		// this is how we distinguish what countries we own
        
        board = theboard;
        countries = board.getCountries();
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
    
    public int pickCountry()
    {
        return -1;
    }
    
    public void placeInitialArmies( int numberOfArmies )
    {
    }
    
    public void cardsPhase( Card[] cards )
    {
    }
    
    public void placeArmies( int numberOfArmies )
    {
    }
    
    public void attackPhase()
    {
    }
    
    public int moveArmiesIn( int cca, int ccd)
    {
        return 0;
    }
    
    public void fortifyPhase()
    {
    }
    
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
    
}
